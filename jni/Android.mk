LOCAL_PATH := $(call my-dir)
########################################
# NCI Configuration
########################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := VideoOnlineHost.cpp

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_CERTIFICATE := platform

LOCAL_SHARED_LIBRARIES := \
    libcutils libutils libui libgui \
    liblog libandroid_runtime libstagefright_foundation\

LOCAL_MODULE := libvideoonline

include $(BUILD_SHARED_LIBRARY)

########################################
# NCI Configuration
########################################
include $(CLEAR_VARS)

LOCAL_SRC_FILES := android_coop_HostPlay.cpp

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_CERTIFICATE := platform

LOCAL_SHARED_LIBRARIES := \
    libaudioflinger libcutils libbinder libutils \
    liblog libmedia libhostplay libandroid_runtime \
	libgui libvideoshare libvideoonline\

LOCAL_MODULE := libmedia_host

LOCAL_C_INCLUDES := \
		$(TOP)/hardware/qcom/audio/hal

include $(BUILD_SHARED_LIBRARY)
