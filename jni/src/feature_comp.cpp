#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <vector>
#include <string.h>
#include <android/log.h>

#define TAG "FindFeatures"

using namespace std;
using namespace cv;

extern "C" {
JNIEXPORT void JNICALL Java_com_google_code_panoforandroid_PanoSurfaceView_FindFeatures(JNIEnv* env, jobject thiz, jlong addrGray, jlong addrRgba, jlong addrComp)
{
    Mat* pMatGr=(Mat*)addrGray;
    Mat* pMatRgb=(Mat*)addrRgba;
    Mat* pMatCom=(Mat*)addrComp;

    Mat smallCom;

    vector<KeyPoint> v;

    Mat descriptors1, descriptors2;
    vector<KeyPoint> keypoints1, keypoints2;
    vector<DMatch> matches;
    vector<char> empty;

    SurfFeatureDetector detector(2500.);
    SurfDescriptorExtractor surfDesc;
    FlannBasedMatcher matcher;

    resize(*pMatCom, smallCom, Size(), 0.25, 0.25);

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%s]", "pre surf compute");

    detector.detect(smallCom, keypoints1);
    surfDesc.compute(smallCom, keypoints1, descriptors1);
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%d]", keypoints1.size());


    detector.detect(*pMatGr, keypoints2);
    surfDesc.compute(*pMatGr,  keypoints2, descriptors2);
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%d]", keypoints2.size());

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%s]", "pre match");
    matcher.match(descriptors1, descriptors2, matches);

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%s]", "pre reduce");
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%d]", matches.size());
    nth_element(matches.begin(), matches.begin()+10, matches.end());
    matches.erase(matches.begin()+11, matches.end());

    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%d]", matches.size());
    __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK [%s]", "pre draw");

    for( size_t i = 0; i < matches.size(); i++) {
        circle(*pMatRgb, Point(keypoints2[matches[i].trainIdx].pt.x,
                keypoints2[matches[i].trainIdx].pt.y), 10, Scalar(255,0,0,255));
        __android_log_print(ANDROID_LOG_DEBUG, TAG, "NDK Distance [%f]", matches[i].distance);
    }


/*
    drawMatches(*pMatCom, keypoints1,
            *pMatGr, keypoints2,
            matches, *pMatRgb,
            Scalar(255,255,255,255),
            Scalar(255,0,0,255),
            empty, 1);
    FastFeatureDetector detector(50);
    detector.detect(*pMatGr, v);
    for( size_t i = 0; i < keypoints2.size(); i++ )
        circle(*pMatRgb, Point(keypoints2[i].pt.x, keypoints2[i].pt.y), 10, Scalar(255,0,0,255));
*/
}

}
