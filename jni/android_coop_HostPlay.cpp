#include <string.h>
#include <jni.h>
#include "JNIHelp.h"
#include <stdlib.h>
#include <utils/Log.h>
#include "android_runtime/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <sys/resource.h>
#include <binder/IPCThreadState.h>

#include "CblkMemory.h"
#include <media/IAudioFlinger.h>
#include <media/HostPlay.h>
//vaylb video
#include <gui/ISurfaceComposer.h>
#include <private/gui/ComposerService.h>

#include <binder/IPCThreadState.h>  
#include <binder/ProcessState.h>  
#include <binder/IServiceManager.h> 
#include <videoshare/VideoShare.h>
#include "VideoOnlineHost.h"
#include <android_runtime/android_view_Surface.h> 


using namespace android;

sp<HostPlay> mHostPlay;
sp<VideoShare> mVideoShare;
sp<VideoOnlineHost> mVideoOnline;
sp<Surface> surface; 
void* buffer;
JavaVM *g_jvm = NULL;
jobject g_obj = NULL;
JNIEnv *env;
jclass cls;
jmethodID mid;
jmethodID m_videoformat_id; //setupMediaFormat
jmethodID m_newvideodata_id; //onNewVideoData
void getJNIEnv(bool* needDetach) {
	ALOGE("vaylb->getJniEnv run");
	*needDetach = false;
	if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
		ALOGE("pzhao-->AttachCurrentThread fail");
		return;
	}
	cls = env->GetObjectClass(g_obj);
	if (cls == NULL) {
		ALOGE("pzhao-->find class error");
		return;
	}
	mid = env->GetMethodID(cls, "fromJni", "(I)V");
	if (mid == NULL) {
		ALOGE("pzhao-->find method error");
		return;
	}
	m_videoformat_id = env->GetMethodID(cls, "setupMediaFormat", "(III)V");
	if (m_videoformat_id == NULL) {
		ALOGE("vaylb-->find method setupMediaFormat error");
		return;
	}
	
	m_newvideodata_id = env->GetMethodID(cls, "onNewVideoData", "(IJ)V"); //long--J
	if (m_newvideodata_id == NULL) {
		ALOGE("vaylb-->find method onNewVideoData error");
		return;
	}
	*needDetach = true;
	ALOGE("vaylb->getJniEnv success");
}

void detachJNI() {
	if (g_jvm->DetachCurrentThread() != JNI_OK)
		ALOGD("pzhao-->DetachCurrentThread fail");
	ALOGE("pzhao->detachJniEnv success");
}

void fun(int x) {
//	JNIEnv * env;
//	jclass cls;
//	jmethodID mid;
//	ALOGE("pzhao->call back success %d", x);
//	if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
//		ALOGE("pzhao-->AttachCurrentThread fail");
//		return;
//	}
//	cls = env->GetObjectClass(g_obj);
//	if (cls == NULL) {
//		ALOGE("pzhao-->find class error");
//		goto error;
//	}
//	mid = env->GetMethodID(cls, "fromJni", "(I)V");
//	if (mid == NULL) {
//		ALOGE("pzhao-->find method error");
//		goto error;
//	}
	env->CallVoidMethod(g_obj, mid, x);
//	error: if (g_jvm->DetachCurrentThread() != JNI_OK)
//		ALOGD("pzhao-->DetachCurrentThread fail");
}

jint android_coop_HostPlay_native_setup(JNIEnv * env, jobject obj,
		jint receivebuffersize, jint sendbuffersize) {
	ALOGE("audio_test-->JNI setup");
	if(g_jvm==NULL) env->GetJavaVM(&g_jvm);
	if(g_obj==NULL) g_obj = env->NewGlobalRef(obj);
	mHostPlay = new HostPlay();
	mHostPlay->setcallback(fun, getJNIEnv, detachJNI);
	return mHostPlay->create(receivebuffersize, sendbuffersize);
}

//add pzhao
jint android_coop_HostPlay_native_haswrite(JNIEnv * env, jobject obj) {
	if (mHostPlay != NULL) {
		return mHostPlay->haswrite;
	}
	return 0;
}

jboolean android_coop_HostPlay_native_needcheckwrited(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return false;
	return mHostPlay->needcheckwrited();
}

void android_coop_HostPlay_native_setstartflag(JNIEnv * env, jobject obj,
		jint flag) {
	if (mHostPlay != NULL) {
		mHostPlay->setstartflag(flag);
	}
}

jboolean android_coop_HostPlay_native_checkstandbyflag(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return true;
	return mHostPlay->standbyflag;
}

jboolean android_coop_HostPlay_native_checkreadpos(JNIEnv * env, jclass clazz) {
	if (mHostPlay == NULL)
		return false;
//	ALOGE("pzhao->TCP thread check readpos");
	return mHostPlay->checkCanRead();
}

jboolean android_coop_HostPlay_native_checkexitflagI(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return true;
	return mHostPlay->exitflag1;
}

jboolean android_coop_HostPlay_native_checkexitflagII(JNIEnv * env,
		jclass clazz) {
	if (mHostPlay == NULL)
		return true;
	return mHostPlay->exitflag2;
}

void android_coop_HostPlay_native_setbuffertemp(JNIEnv* env, jobject thiz,
		jobject sendbuffer) {
	buffer = env->GetDirectBufferAddress(sendbuffer);
	jlong length = env->GetDirectBufferCapacity(sendbuffer);
	if (mHostPlay == NULL)
		return;
	mHostPlay->setBufferTemp(buffer, length >> 1); //length for 16bits
}

void android_coop_HostPlay_native_setplayflag(JNIEnv * env, jclass clazz,
		jlong time_java) {
	if (mHostPlay == NULL)
		return;
	ALOGE("pzhao-->JNI::native_setplayflag");
	mHostPlay->changePlayFlag(true);
	mHostPlay->time_delay_flag = true;
	struct timeval tv;
	gettimeofday(&tv, NULL);
	long time_jni_host = tv.tv_sec * 1000000 + tv.tv_usec;
	ALOGE("pzhao-->host setPlayFlag:%fms", time_jni_host / 1000.0);
	mHostPlay->mHandle->time_delay_host(time_java);
}

void android_coop_HostPlay_native_setreadpos(JNIEnv * env, jclass clazz,
		jint pos) {
	if (mHostPlay == NULL)
		return;
//	ALOGE("pzhao->TCP thread set readpos %d",pos);
	mHostPlay->sendReadpos = pos >> 1;  //length for 16bits
}

void android_coop_HostPlay_native_read_ahead(JNIEnv * env, jobject obj,
		jint readahead) {
	if (mHostPlay == NULL)
		return;
	ALOGE("audio_test-->JNI native_read_ahead %d",readahead);
	mHostPlay->readaheadflag = true;
	mHostPlay->readaheadcount = readahead;
}

void android_coop_HostPlay_native_exit(JNIEnv* env, jclass clazz) {
	ALOGE("audio_test-->JNI native_exit run.");
	mHostPlay->exit();
	mHostPlay.clear();
	if(buffer!=NULL)
		free(buffer);
}

void android_coop_HostPlay_native_sinagle_to_write(JNIEnv * env, jobject obj) {
	if (mHostPlay == NULL)
		return;
//	ALOGE("pzhao->singalToWrite");
	mHostPlay->singalToWrite();
}

void  android_coop_HostPlay_native_setvideohook(JNIEnv * env, jobject obj, jint flag)
{
	ALOGE("vaylb-->jni::setvideohook = %d",flag);

	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {
		sm->setVideoHook(flag);
    }
}

void  android_coop_HostPlay_native_setslaveip(JNIEnv * env, jobject obj, jstring ip)
{
	/*
	if(mVideoShare != NULL){
		const char* chars = env->GetStringUTFChars(ip, 0);
  		String16 str_16;
  		str_16.append((const char16_t*)chars,strlen(chars));
		ALOGE("vaylb-->jni::setslaveip = %s",str_16.string());
		mVideoShare->setUpSlaveIp(str_16);
		env->ReleaseStringUTFChars(ip, chars);
	}
	*/	

	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {	
		const char* chars = env->GetStringUTFChars(ip, 0);
  		String16 str_16;
  		str_16.append((const char16_t*)chars,strlen(chars));
		ALOGE("vaylb-->jni::setslaveip = %s",str_16.string());
		sm->setSlaveIp(str_16);
		env->ReleaseStringUTFChars(ip, chars);
    }
}

void  android_coop_HostPlay_native_setslavenum(JNIEnv * env, jobject obj, jint num)
{
	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {	
		ALOGE("vaylb-->jni::setslavenum = %d",num);
		sm->setSlaveNum(num);
    }
}

void  android_coop_HostPlay_initVideoShareClient()
{
	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {	
		ALOGE("vaylb-->jni::initVideoShareClient");
		sm->initVideoShareClient();
    }
}

void setupMediaFormat4Java(int width, int height, int csd_size) {
	env->CallVoidMethod(g_obj, m_videoformat_id, width, height, csd_size);
}

void onNewVideoData4Java(int size, long pts){
	env->CallVoidMethod(g_obj, m_newvideodata_id, size, pts);
}

void  android_coop_HostPlay_native_videoonline_init(JNIEnv * env, jobject obj, jstring local_ip, jstring gateway_ip)
{
	if(g_jvm==NULL) env->GetJavaVM(&g_jvm);
	if(g_obj==NULL) g_obj = env->NewGlobalRef(obj);

	const char* chars = env->GetStringUTFChars(local_ip, 0);
	const char* chars_gateway = env->GetStringUTFChars(gateway_ip, 0);
  	String16 str_16,str_16_gateway;
  	str_16.append((const char16_t*)chars,strlen(chars));
	str_16_gateway.append((const char16_t*)chars_gateway,strlen(chars_gateway));
	if(mVideoOnline == NULL){
		mVideoOnline = new VideoOnlineHost();
	}
	mVideoOnline->setcallback(setupMediaFormat4Java, onNewVideoData4Java, getJNIEnv, detachJNI);
	mVideoOnline->getConnectedIP(str_16,str_16_gateway);	
	env->ReleaseStringUTFChars(local_ip, chars);
	env->ReleaseStringUTFChars(gateway_ip, chars_gateway);
	mVideoOnline->start_threads();
}

void  android_coop_HostPlay_native_videoonline_exit(JNIEnv * env, jobject obj)
{
	ALOGE("vaylb-->jni::video online exit.");
	mVideoOnline->stop_threads();
}

jboolean android_coop_HostPlay_videoonline_setVideoSurface(JNIEnv *env, jobject thiz, jobject jsurface){ 
	if(mVideoOnline == NULL){
		mVideoOnline = new VideoOnlineHost();
	} 
    surface = android_view_Surface_getSurface(env, jsurface);  
    if(android::Surface::isValid(surface)){  
		return mVideoOnline->setVideoSurface(surface);
    }else {  
        ALOGE("surface is invalid ");  
        return false;  
    }    
}

jboolean android_coop_HostPlay_videoonline_getcsd(JNIEnv* env, jobject thiz, jobject csdbuffer, jint size) {
	void * csd_buffer = env->GetDirectBufferAddress(csdbuffer);
	if (mVideoOnline == NULL) return false;
	return mVideoOnline->setVideoCSD(csd_buffer,size);
}

void android_coop_HostPlay_videoonline_setVideoBuffer(JNIEnv* env, jobject thiz, jobject buffer_in, jobject buffer_out) {
	void * pkt_buffer_in = env->GetDirectBufferAddress(buffer_in);
	jlong length_in = env->GetDirectBufferCapacity(buffer_in);
	void * pkt_buffer_out = env->GetDirectBufferAddress(buffer_out);
	jlong length_out = env->GetDirectBufferCapacity(buffer_out);
	if (mVideoOnline == NULL) return;
	mVideoOnline->setVideoBuffer(pkt_buffer_in,length_in,pkt_buffer_out,length_out);
}

void  android_coop_HostPlay_native_videoonline_setBufInStat(JNIEnv * env, jobject obj, jboolean stat)
{
	if (mVideoOnline == NULL) return;
	mVideoOnline->updateVideoBufInState(stat);
}

jboolean  android_coop_HostPlay_native_videoonline_getBufInStat(JNIEnv * env, jobject obj)
{
	if (mVideoOnline == NULL) return false;
	return mVideoOnline->isVideoBufInAccess();
}

void  android_coop_HostPlay_native_setscreensplit(JNIEnv * env, jobject obj, jint flag)
{
	ALOGE("vaylb-->jni::setscreensplit = %d",flag);

	sp<ISurfaceComposer> sm(ComposerService::getComposerService());
    if (sm != 0) {
		sm->setScreenSplit(flag);
    }
}

static JNINativeMethod gMethods[] = { 
		{ "native_setup", "(II)I",(void*) android_coop_HostPlay_native_setup }, 
		{ "native_haswrite","()I", (void*) android_coop_HostPlay_native_haswrite }, 
		{ "native_setstartflag", "(I)V", (void*) android_coop_HostPlay_native_setstartflag }, 
		{ "native_read_ahead", "(I)V", (void*) android_coop_HostPlay_native_read_ahead }, 
		{ "native_checkstandbyflag", "()Z", (void*) android_coop_HostPlay_native_checkstandbyflag }, 
		{ "native_checkreadpos", "()Z", (void*) android_coop_HostPlay_native_checkreadpos }, 
		{ "native_checkexitflagI", "()Z", (void*) android_coop_HostPlay_native_checkexitflagI }, 
		{ "native_checkexitflagII", "()Z", (void*) android_coop_HostPlay_native_checkexitflagII }, 
		{ "native_setplayflag", "(J)V", (void*) android_coop_HostPlay_native_setplayflag }, 
		{ "native_setreadpos", "(I)V", (void*) android_coop_HostPlay_native_setreadpos }, 
		{ "native_setbuffertemp", "(Ljava/nio/ByteBuffer;)V", (void*) android_coop_HostPlay_native_setbuffertemp }, 
		{ "native_exit", "()V", (void*) android_coop_HostPlay_native_exit }, 
		{ "native_needcheckwrited", "()Z", (void*) android_coop_HostPlay_native_needcheckwrited }, 
		{ "native_signaleToWrite", "()V", (void*) android_coop_HostPlay_native_sinagle_to_write },
		{ "native_setslaveip","(Ljava/lang/String;)V", (void*) android_coop_HostPlay_native_setslaveip}, 
		{ "native_setvideohook", "(I)V", (void*) android_coop_HostPlay_native_setvideohook },
		{ "native_setslavenum","(I)V", (void*) android_coop_HostPlay_native_setslavenum},
		{ "native_videoonline_init", "(Ljava/lang/String;Ljava/lang/String;)V", (void*) android_coop_HostPlay_native_videoonline_init },
		{ "native_videoonline_exit", "()V", (void*) android_coop_HostPlay_native_videoonline_exit },
		{ "native_videoonline_setVideoSurface", "(Landroid/view/Surface;)Z", (void*)android_coop_HostPlay_videoonline_setVideoSurface},
		{ "native_videoonline_getcsd", "(Ljava/nio/ByteBuffer;I)Z", (void*) android_coop_HostPlay_videoonline_getcsd}, 
		{ "native_videoonline_setVideoBuffer", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V", 								(void*)android_coop_HostPlay_videoonline_setVideoBuffer}, 
		{ "native_videoonline_getPktInStat", "()Z", (void*) android_coop_HostPlay_native_videoonline_getBufInStat },
		{ "native_videoonline_setPktInStat", "(Z)V", (void*) android_coop_HostPlay_native_videoonline_setBufInStat },
		{ "native_setscreensplit", "(I)V", (void*) android_coop_HostPlay_native_setscreensplit },
};

static int register_android_coop_HostPlay(JNIEnv *env) {
	return AndroidRuntime::registerNativeMethods(env, "com/zhao/host/HostPlay",
			gMethods, NELEM(gMethods));
}

JNIEXPORT jint JNI_OnLoad(JavaVM *jvm, void *reserved) {
	JNIEnv *e;
	int status;
	if (jvm->GetEnv((void**) &e, JNI_VERSION_1_6) != JNI_OK) {
		return JNI_ERR;
	}
	ALOGE("vaylb_test-->JNI: jni_onload().");
	if ((status = register_android_coop_HostPlay(e)) < 0) {
		ALOGE("jni Mainactivity registration failure, status: %d", status);
		return JNI_ERR;
	}

	if(mVideoShare==NULL) mVideoShare = new VideoShare();
	mVideoShare->setcallback(fun);
	VideoShare::instantiate(mVideoShare);

	android_coop_HostPlay_initVideoShareClient();

	//ProcessState::self()->startThreadPool();
	//IPCThreadState::self()->joinThreadPool();

	return JNI_VERSION_1_6;
}


JNIEXPORT void JNI_OnUnload(JavaVM *jvm, void *reserved) {
	ALOGE("vaylb_test-->JNI: jni_OnUnload().");
	mHostPlay.clear();
	//mHostPlay = NULL;
}

