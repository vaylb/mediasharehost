#ifndef ANDROID_MEDIA_VIDEO_ONLINE_HOST_H
#define ANDROID_MEDIA_VIDEO_ONLINE_HOST_H

#include <utils/threads.h>
#include <utils/Debug.h>
#include <utils/Thread.h>
#include <pthread.h>

//vaylb for socket
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <gui/Surface.h>
#define RECV_BUF_COUNT 4
#define	RECV_BUF_SIZE 524288

namespace android {

class VideoOnlineHost : public virtual RefBase
{
public:

            	 VideoOnlineHost();

protected:
          virtual ~VideoOnlineHost();
public:
				status_t		getConnectedIP(String16 local_ip, String16 gateway_ip);
				status_t		initSocket(String16 local_ip, String16 gateway_ip);
				bool			setVideoSurface(sp<Surface>& surface);
				bool			setVideoCSD(void* buffer, int size);
				void			setVideoBuffer(void* buffer_in, int size_in,void* buffer_out, int size_out);
				void			sleep(long time);
				void 			start_threads();
				void 			stop_threads();
				void 			setcallback(void (*mediaformat)(int,int,int),void (*newdata)(int,long),void(*getJni)(bool*),void(*detachJni)());
				void  			(*setMediaFormat)(int,int,int);
				void  			(*onNewVideoData)(int,long);
				void 			(*getJniEnv)(bool*);
				void 			(*detachJniEnv)();
				bool			isVideoBufInAccess();
				void			updateVideoBufInState(bool stat);

	enum BufState{
		can_read = 0,
		can_write
	};
	
	int 						mIndex;
	String16					mHostIp;
	String16					mLocalIp;
	String16					mGatewayIp;
	sp<Surface>					mSurface;
	//unsigned char*				mRecvBuf[RECV_BUF_COUNT];	
	//BufState					mRecvBufState[RECV_BUF_COUNT];
	
	unsigned char*				mImageBuf;
	unsigned char*				mCSD;
	uint8_t *					mVideoBufIn;
	uint8_t *					mVideoBufOut;
	int							mVideoBufSizeIn;
	int							mVideoBufSizeOut;
	bool						mBufferInAccess; //true-java read, false C++ write
	bool						mBufferOutAccess; //true-java write, false C++ read


	class VideoReceiver: public Thread {

	public:
								VideoReceiver(VideoOnlineHost* videoonline);
    	virtual 				~VideoReceiver();

    	virtual     bool        threadLoop();
		
					void		deCompressFromJPEG(unsigned char * src_buffer,unsigned long src_size,unsigned char** outbuf,int *width,int *height);
					void        threadLoop_exit();
					void        threadLoop_run();
					void		signalVideoPlayer();
					void		sleep(long time);

	private:
		sp<VideoOnlineHost>		mVideoOnline;
		bool						needdetach;
	};  // class VideoReceiver

	class VideoPlayer : public Thread {

	public:
			VideoPlayer(VideoOnlineHost* host);
    		virtual ~VideoPlayer();
		    virtual     bool        threadLoop();
			void		videoPlayBack(unsigned char * image_buf,int width,int height);
			void		frameRender(const void *data, const sp<ANativeWindow> &nativeWindow,int width,int height);
			void        threadLoop_exit();
			void        threadLoop_run();

	private:
		sp<VideoOnlineHost>		mVideoOnline;
	};  // class VideoPlayer

	private:
		sp<VideoReceiver>		mVideoReceiver;
		sp<VideoPlayer>			mVideoPlayer;
};



}; // namespace android

#endif // ANDROID_MEDIA_VIDEO_ONLINE_HOST_H
