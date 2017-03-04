
#define LOG_TAG "VideoOnlineHost"
#define BUFFER_SIZE 4096

//for use the GL & EGL extension
#define EGL_EGLEXT_PROTOTYPES
#define GL_GLEXT_PROTOTYPES

#define UpAlign4(n) (((n) + 3) & ~3)
#define ALIGN(x,y) ((x + y - 1) & ~(y - 1))


#include <sys/resource.h>
#include <binder/IPCThreadState.h>
#include <utils/Log.h>
#include <cutils/atomic.h>
#include <time.h>
#include <utils/Trace.h>
#include <cutils/properties.h>

#include "VideoOnlineHost.h"

#include <ui/GraphicBuffer.h>
#include <ui/GraphicBufferMapper.h>
#include <media/stagefright/foundation/ADebug.h> 



using namespace android;
	
static		int							mGatewaySocketFd;
static		struct sockaddr_in			mGatewayAddress;

static		Mutex						mPlayerLock;
static		Condition					mPlayerCondition;

static		bool						mJpegBufInUse;
static		int							mCurrentWidth;
static		int							mCurrentHeight;

	
//----------------------------------------------------------------------------------------------------
//socket read
int socket_read(int fd,unsigned char *buffer,int length) 
{ 
	//ALOGE("vaylb-->socket_read, total size = %d",length);
	int i = length;
    int ret = 0;
    while(i > 0 && (ret = read(fd,buffer + (length - i),i)) > 0)
    {
           i -= ret;
    }
    return (i == 0)?length:0;
}

//----------------------------------------------------------------------------------------------------

long get_time_stamp(){ //ms
	struct timeval tv;	  
	gettimeofday(&tv,NULL);    
	return (tv.tv_sec * 1000 + tv.tv_usec / 1000);
}

//----------------------------------------------------------------------------------------------------


VideoOnlineHost::VideoOnlineHost(/*sp<BufferQueue> bufferQueue*/):
	mImageBuf(NULL),
	mCSD(NULL),
	mBufferInAccess(false),
	mVideoReceiver(new VideoReceiver(this)),
	mVideoPlayer(new VideoPlayer(this))
{
	mJpegBufInUse = false;
	ALOGE("vaylb_test-->VideoOnlineHost construct.");
}

VideoOnlineHost::~VideoOnlineHost()
{
	ALOGE("vaylb_test-->VideoOnlineHost destruct.");
	/*
	for(int i = 0; i < RECV_BUF_COUNT; i++){
		free(mRecvBuf[i]);
		mRecvBuf[i] = NULL;
	}
	*/
	if(mVideoReceiver != NULL) mVideoReceiver->~VideoReceiver();
	if(mVideoPlayer != NULL) mVideoPlayer->~VideoPlayer();
	if(mImageBuf != NULL) {
		free(mImageBuf);
		mImageBuf = NULL;
	}
	if(mCSD != NULL){
		free(mCSD);
		mCSD = NULL;
	}
}

status_t VideoOnlineHost::initSocket(String16 local_ip,String16 gateway_ip){
	mLocalIp = local_ip;
	mGatewayIp = gateway_ip;

	int res;	
	mGatewaySocketFd = socket(AF_INET,SOCK_STREAM,IPPROTO_TCP);
	if(mGatewaySocketFd == -1){
		ALOGE("vaylb-->create socket error:%d",errno); //errno13 :    Permission denied
		return 0;
	}

	mGatewayAddress.sin_family = AF_INET;
	mGatewayAddress.sin_addr.s_addr = inet_addr((const char*)(mGatewayIp.string()));
	mGatewayAddress.sin_port = htons(12307);
	
	res = connect(mGatewaySocketFd,(struct sockaddr*)&mGatewayAddress, sizeof(mGatewayAddress));
	if(res == -1){
		ALOGE("vaylb-->connect to gateway error:%d",errno);
		return 0;
	}
	return 1;
}

status_t VideoOnlineHost::getConnectedIP(String16 local_ip,String16 gateway_ip){
	if(!initSocket(local_ip,gateway_ip)){
		ALOGE("vaylb-->VideoOnlineHost initSocket error %d",errno);
		return -1;
	}
	int res = 0;
	char command = 'P';
	res = write(mGatewaySocketFd,(const void*)&command,sizeof(command));
	if(res == -1)
	{
		ALOGE("vaylb-->VideoOnlineHost send get IPs command error %d",errno);
		return -1;
	}
	int receive = 0;
	read(mGatewaySocketFd,(void*)&receive,sizeof(receive));
	int size = ntohl(receive);
	char* rec_back = (char*)malloc(size);
	res = read(mGatewaySocketFd,rec_back,size); //ack
	if(res == -1)
	{
		ALOGE("vaylb-->VideoOnlineHost get IPs error %d",errno);
		return -1;
	}
	ALOGE("vaylb-->VideoOnlineHost get IPs %s",rec_back);
	//close(mGatewaySocketFd);
	return NO_ERROR;
}

bool VideoOnlineHost::setVideoSurface(sp < Surface > & surface){
	mSurface = surface;
	if(android::Surface::isValid(mSurface)) {
		ALOGE("vaylb-->VideoOnlineHost surface is valid ");  
		return true;
	}
	else return false;
}

bool VideoOnlineHost::setVideoCSD(void* buffer, int size){
	memcpy(buffer,mCSD,size);
	return true;
}

void VideoOnlineHost::setVideoBuffer(void* buffer_in, int size_in,void* buffer_out, int size_out){
	mVideoBufIn = (uint8_t*)buffer_in;
	mVideoBufSizeIn = size_in;
	mVideoBufOut = (uint8_t*)buffer_out;
	mVideoBufSizeOut = size_out;
	return;
}

void VideoOnlineHost::setcallback(void (*mediaformat)(int,int,int),void (*newdata)(int,long),void(*getJni)(bool*),void(*detachJni)()){
	setMediaFormat=mediaformat;
	onNewVideoData = newdata;
	getJniEnv=getJni;
	detachJniEnv=detachJni;
}

bool VideoOnlineHost::isVideoBufInAccess(){
	return mBufferInAccess;
}

void VideoOnlineHost::updateVideoBufInState(bool stat){
	mBufferInAccess = stat;
}

void VideoOnlineHost::start_threads(){
	if(mVideoReceiver != NULL) mVideoReceiver->threadLoop_run();
	//if(mVideoPlayer != NULL) mVideoPlayer->threadLoop_run();
}

void VideoOnlineHost::stop_threads(){
	if(mVideoReceiver != NULL) mVideoReceiver->threadLoop_exit();
	if(mVideoPlayer != NULL) mVideoPlayer->threadLoop_exit();
	mVideoReceiver->signalVideoPlayer();
}


/**
*mediacodec http://blog.csdn.net/halleyzhang3/article/details/11473961
*/

//-----------------------------------------------------------------------------
//                        				VideoDecodecor                     
//-----------------------------------------------------------------------------
VideoOnlineHost::VideoReceiver::VideoReceiver(VideoOnlineHost * videosonlinehost)
	:Thread(false /*canCallJava*/),
	mVideoOnline(videosonlinehost),
	needdetach(false)
{
	ALOGE("vaylb-->VideoReceiver construct.");
}

VideoOnlineHost::VideoReceiver::~VideoReceiver()
{
	ALOGE("vaylb-->VideoReceiver destruct.");
	mVideoOnline.clear();
	mVideoOnline = NULL;
}

bool VideoOnlineHost::VideoReceiver::threadLoop()
{
	ALOGE("vaylb-->VideoReceiver threadLoop");

	mVideoOnline->getJniEnv(&needdetach);
	
	int recId = -1;
	int video_width = 0, video_height = 0, csd_size = 0;
	//unsigned char * csd = NULL;
	int64_t pkt_pts = 0, pkt_dts = 0;
	int pkt_size = 0;
	char command = 'V';
	int res = write(mGatewaySocketFd,(const void*)&command,sizeof(command));
	if(res == -1)
	{
		ALOGE("vaylb-->VideoOnlineHost send get media command error %d",errno);
		return -1;
	}
	
	read(mGatewaySocketFd,(void*)&video_width,sizeof(video_width));
	read(mGatewaySocketFd,(void*)&video_height,sizeof(video_height));
	read(mGatewaySocketFd,(void*)&csd_size,sizeof(csd_size));

	video_width = ntohl(video_width);
	video_height = ntohl(video_height);
	csd_size = ntohl(csd_size);
	mVideoOnline->mCSD = (unsigned char*)malloc(csd_size);
	res = read(mGatewaySocketFd,mVideoOnline->mCSD,csd_size);
	mVideoOnline->setMediaFormat(video_width,video_height,csd_size); //tell java this parms
	
	bool flag = true;
	while (!exitPending())
    {
    	read(mGatewaySocketFd,(void*)&pkt_pts,sizeof(pkt_pts)); 
		read(mGatewaySocketFd,(void*)&pkt_dts,sizeof(pkt_dts)); 
		read(mGatewaySocketFd,(void*)&pkt_size,sizeof(pkt_size)); 
		pkt_pts = ntohl(pkt_pts);
		pkt_dts = ntohl(pkt_dts);
		pkt_size = ntohl(pkt_size);
		long time1 = get_time_stamp();

		if(pkt_size <= 0){
			ALOGE("receive new pkt_size error:%d",pkt_size);
			continue;
		}
		//ALOGE("vaylb-->receive new packet pts = %lld, dts = %lld, size = %d",pkt_pts,pkt_dts,pkt_size);
		
		uint8_t* packet = (uint8_t*)malloc(pkt_size);
		if(socket_read(mGatewaySocketFd,packet,pkt_size)){
			while(mVideoOnline->isVideoBufInAccess()) sleep(1000000); //1ms, only false can native write
			memcpy(mVideoOnline->mVideoBufIn,packet,pkt_size);
			mVideoOnline->updateVideoBufInState(true);
			mVideoOnline->onNewVideoData(pkt_size,pkt_pts);
			//ALOGE("vaylb-->receive packet success pts = %lld, dts = %lld,  size = %d",pkt_pts,pkt_dts,pkt_size);
		}	
		free(packet);
		packet = NULL;
    }
	if(needdetach)
		mVideoOnline->detachJniEnv();
	ALOGE("vaylb-->VideoReceiver::threadLoop end.");
    return false;
}

void VideoOnlineHost::VideoReceiver::sleep(long sleepNs){
	const struct timespec req = {0, sleepNs};
	nanosleep(&req, NULL);
}

void VideoOnlineHost::VideoReceiver::signalVideoPlayer(){
	Mutex::Autolock _l(mPlayerLock);
	mPlayerCondition.signal();
}

void VideoOnlineHost::VideoReceiver::threadLoop_run(){
	run("VideoReceiver", PRIORITY_URGENT_DISPLAY);
}

void VideoOnlineHost::VideoReceiver::threadLoop_exit(){
	close(mGatewaySocketFd);
	this->requestExit();
	//this->requestExitAndWait();
}


//-----------------------------------------------------------------------------
//                        				VideoPlayer                    
//-----------------------------------------------------------------------------
VideoOnlineHost::VideoPlayer::VideoPlayer(VideoOnlineHost* videoonline)
	:Thread(false /*canCallJava*/),
	mVideoOnline(videoonline)
{
	ALOGE("vaylb-->VideoOnlineHost VideoPlayer construct.");
}

VideoOnlineHost::VideoPlayer::~VideoPlayer()
{
	ALOGE("vaylb-->VideoOnlineHost VideoPlayer destruct.");
	mVideoOnline.clear();
	mVideoOnline = NULL;
}

bool VideoOnlineHost::VideoPlayer::threadLoop()
{
	ALOGE("vaylb-->VideoOnlineHost VideoPlayer  threadLoop");
	
	while (!exitPending())
    {
    	Mutex::Autolock _l(mPlayerLock);
		mPlayerCondition.wait(mPlayerLock);
		//ALOGE("vaylb-->VideoPlayer playback new frame width = %d, height = %d",mCurrentWidth,mCurrentHeight);
		videoPlayBack(mVideoOnline->mImageBuf,mCurrentWidth,mCurrentHeight);
		mJpegBufInUse = false;
    }
	ALOGE("vaylb_test-->VideoPlayer threadLoop end.");
    return false;
}

void VideoOnlineHost::VideoPlayer::videoPlayBack(unsigned char * image_buf,int width,int height){
	frameRender(image_buf,mVideoOnline->mSurface,width,height);
}

void VideoOnlineHost::VideoPlayer::frameRender(const void * data,const sp < ANativeWindow > & nativeWindow,int width,int height){
	sp<ANativeWindow> mNativeWindow = nativeWindow;  
    int err;  
    int mCropWidth = width;  
    int mCropHeight = height;  
      
    int halFormat = HAL_PIXEL_FORMAT_YV12;
    int bufWidth = (mCropWidth + 1) & ~1;
    int bufHeight = (mCropHeight + 1) & ~1;  
      
    CHECK_EQ(0,  
            native_window_set_usage(  
            mNativeWindow.get(),  
            GRALLOC_USAGE_SW_READ_NEVER | GRALLOC_USAGE_SW_WRITE_OFTEN  
            | GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_EXTERNAL_DISP));  
  
    CHECK_EQ(0,  
            native_window_set_scaling_mode(  
            mNativeWindow.get(),  
            NATIVE_WINDOW_SCALING_MODE_SCALE_CROP));  
  
    CHECK_EQ(0, native_window_set_buffers_geometry(  
                mNativeWindow.get(),  
                bufWidth,  
                bufHeight,  
                halFormat));  
      
      
    ANativeWindowBuffer *buf;
    if ((err = native_window_dequeue_buffer_and_wait(mNativeWindow.get(),&buf)) != 0) {  
        ALOGW("vaylb-->slave ::dequeueBuffer returned error %d", err);  
        return;  
    }  
  
    GraphicBufferMapper &mapper = GraphicBufferMapper::get();  
  
    Rect bounds(mCropWidth, mCropHeight);  
  
    void *dst;  
    CHECK_EQ(0, mapper.lock(buf->handle, GRALLOC_USAGE_SW_WRITE_OFTEN, bounds, &dst));
     
    size_t dst_y_size = buf->stride * buf->height;  
    size_t dst_c_stride = ALIGN(buf->stride / 2, 16);
    size_t dst_c_size = dst_c_stride * buf->height / 2;
          
    memcpy(dst, data, dst_y_size + dst_c_size*2);   
  
    CHECK_EQ(0, mapper.unlock(buf->handle));  
  
    if ((err = mNativeWindow->queueBuffer(mNativeWindow.get(), buf,-1)) != 0) {  
        ALOGW("vaylb-->slave::queueBuffer returned error %d", err);  
    }  
    buf = NULL;
}

void VideoOnlineHost::VideoPlayer::threadLoop_run(){
	run("VideoPlayer", PRIORITY_URGENT_DISPLAY);
}

void VideoOnlineHost::VideoPlayer::threadLoop_exit(){
	this->requestExit();
	//this->requestExitAndWait();
}

//}; // namespace android

