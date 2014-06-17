#include <jni.h>
#include <stdlib.h>
#include <math.h>
#include <pthread.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>
using namespace cv;

#define  PRINT_LOG  0
#if      PRINT_LOG
#define  LOG_TAG    "CameraHDR"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#else
#define  LOGI
#define  LOGE
#endif

int   windowsize=15;
int   localwindowsize=20;
float eps=0.001;
float fog_reservation_factor=0.95;


Mat boxfilter(Mat &im, int r);
Mat guildedfilter_color(const Mat &Img, Mat &p, int r, float &epsi);

void  process( Mat &image)
{
    LOGE("line=%d", __LINE__);
    int dimr=image.rows;
    int dimc=image.cols;
    Mat rawImage;
    Mat rawtemp;
    Mat refinedImage;
    Mat refinedImage_temp;
    rawImage.create(image.rows,image.cols,CV_8U); 
    refinedImage_temp.create(image.rows,image.cols,CV_32F);
    rawtemp.create(image.rows,image.cols,CV_8U);

    float sumb=0;float sumg=0;float sumr=0;

    float Air_b;  float Air_g; float Air_r;
    Point2i pt;
    int dimone=floor(dimr*dimc*0.0002);
    int dx=floor(windowsize/2);
    for(int j=0;j<dimr;j++){
        for(int i=0;i<dimc;i++){
            Vec3b c = image.at<Vec3b>(j,i);
	    rawImage.at<uchar>(j,i) = min(c[0], min(c[1], c[2]));
        }
    }
    LOGE("line=%d", __LINE__);
    for(int j=0;j<dimr;j++){
        for(int i=0;i<dimc;i++){
            int min=255;
            int jlow=j-dx;int jhigh=j+dx;
            int ilow=i-dx;int ihigh=i+dx;

            if(ilow<=0) ilow=0;
            if(ihigh>=dimc) ihigh=dimc-1;

            if(jlow<=0) jlow=0;

            if(jhigh>=dimr)  jhigh=dimr-1;

            for(int m=jlow;m<=jhigh;m++){
                for(int n=ilow;n<=ihigh;n++){
                    if(min > rawImage.at<uchar>(m,n))
                        min = rawImage.at<uchar>(m,n);
                }
            }
           rawtemp.at<uchar>(j,i)=min;
        }

    }
    LOGE("line=%d", __LINE__);
    for(int i=0;i<dimone;i++){
        minMaxLoc(rawtemp,0,0,0,&pt);
        Vec3b c = image.at<Vec3b>(pt.y,pt.x);
        sumb+= c[0]; sumg += c[1]; sumr += c[2];
        rawtemp.at<uchar>(pt.y,pt.x)=0;
    }
    LOGE("line=%d", __LINE__);
    Air_b= min(220.f, sumb/dimone);
    Air_g= min(220.f, sumg/dimone);
    Air_r= min(220.f, sumr/dimone);
    float tableR[256];
    float tableG[256];
    float tableB[256];
    for(int i = 0; i < 256; i++) {
        tableR[i] = min(1.0f, (float)i/255.0f);
        tableG[i] = min(1.0f, (float)i/255.0f);
        tableB[i] = min(1.0f, (float)i/255.0f);
    }
    rawtemp.convertTo(rawtemp,CV_32F);
    for (int j=0; j<dimr; j++) {
        for (int i=0; i<dimc; i++) {
        Vec3b c = image.at<Vec3b>(j, i);
        rawtemp.at<float>(j,i) = min(tableB[c[0]], min(tableG[c[1]], tableR[c[2]]));
        }
    }
    LOGE("line=%d", __LINE__);
    for(int j=0;j<dimr;j++){
        for(int i=0;i<dimc;i++){
            float min=1;
            int jlow=j-dx;int jhigh=j+dx;
            int ilow=i-dx;int ihigh=i+dx;
            if(ilow<=0)
                ilow=0;
            if(ihigh>=dimc)
                ihigh=dimc-1;
            if(jlow<=0)
                jlow=0;
            if(jhigh>=dimr)
                jhigh=dimr-1;
            for(int m=jlow;m<=jhigh;m++){
                for(int n=ilow;n<=ihigh;n++){
                    if(min>rawtemp.at<float>(m,n))
                        min=rawtemp.at<float>(m,n);
                }
            }
           rawImage.at<uchar>(j,i)= saturate_cast<uchar> ((1-(float)fog_reservation_factor*min)*255);
        }
   }
   LOGE("line=%d", __LINE__);
   refinedImage_temp = guildedfilter_color(image, rawImage, localwindowsize,eps);
   //rawImage.convertTo( refinedImage_temp, CV_32F);
   //refinedImage_temp /= 255;
   LOGE("line=%d", __LINE__);
   for(int j=0;j<dimr;j++){
       for(int i=0;i<dimc;i++){
           float t = refinedImage_temp.at<float>(j,i);
           refinedImage_temp.at<float>(j,i) = min(1.f, max(0.1f, t));
       }

   }
   LOGE("line=%d", __LINE__);
   for(int j=0;j<dimr;j++){
       for(int i=0;i<dimc;i++){
          float t = refinedImage_temp.at<float>(j,i);
          Vec3b c = image.at<Vec3b>(j, i);
          c[0] = saturate_cast<uchar>( (c[0] - Air_b )/t + Air_b);
          c[1] = saturate_cast<uchar>( (c[1] - Air_g )/t + Air_g);
          c[2] = saturate_cast<uchar>( (c[2] - Air_r )/t + Air_r);
          image.at<Vec3b>(j, i) = c;
       }
   }
   LOGE("line=%d", __LINE__);
}

Mat boxfilter(Mat &im, int r)
{
    //Mat dst;
    //boxFilter(im, dst, -1,  Size(r, r));
    //return dst;
    int hei=im.rows;
    int wid=im.cols;
    cv::Mat imDst;
    cv::Mat imCum;


    imDst=cv::Mat::zeros(hei,wid,CV_32F);
    imCum.create(hei,wid,CV_32F);

    //cumulative sum over Y axis
    for(int i=0;i<wid;i++){
        for(int j=0;j<hei;j++){
            if(j==0)
                imCum.at<float>(j,i)=im.at<float>(j,i);
            else
                imCum.at<float>(j,i)=im.at<float>(j,i)+imCum.at<float>(j-1,i);
        }
    }


    //difference over Y axis
    for(int j=0;j<=r;j++){
        for(int i=0;i<wid;i++){
            imDst.at<float>(j,i)=imCum.at<float>(j+r,i);
        }
    }
    for(int j=r+1;j<=hei-r-1;j++){
        for(int i=0;i<wid;i++){
            imDst.at<float>(j,i)=imCum.at<float>(j+r,i)-imCum.at<float>(j-r-1,i);
        }
    }
    for(int j=hei-r;j<hei;j++){
        for(int i=0;i<wid;i++){
            imDst.at<float>(j,i)=imCum.at<float>(hei-1,i)-imCum.at<float>(j-r-1,i);
        }
    }


    //cumulative sum over X axis
      for(int j=0;j<hei;j++){
          for(int i=0;i<wid;i++){
              if(i==0)
                  imCum.at<float>(j,i)=imDst.at<float>(j,i);
              else
                  imCum.at<float>(j,i)=imDst.at<float>(j,i)+imCum.at<float>(j,i-1);
          }
      }
      //difference over X axis
      for(int j=0;j<hei;j++){
          for(int i=0;i<=r;i++){
              imDst.at<float>(j,i)=imCum.at<float>(j,i+r);
          }
      }
      for(int j=0;j<hei;j++){
          for(int i=r+1;i<=wid-r-1;i++){
              imDst.at<float>(j,i)=imCum.at<float>(j,i+r)-imCum.at<float>(j,i-r-1);
          }
      }
      for(int j=0;j<hei;j++){
          for(int i=wid-r;i<wid;i++){
              imDst.at<float>(j,i)=imCum.at<float>(j,wid-1)-imCum.at<float>(j,i-r-1);
          }
      }

    return imDst;
}

Mat guildedfilter_color(const Mat &Img, Mat &p, int r, float &epsi)

{
    int hei=p.rows;

    int wid=p.cols;
    Mat matOne(hei,wid,CV_32F,Scalar(1));

    Mat N;
    N= boxfilter(matOne, r);

    Mat mean_I_b(hei,wid,CV_32F);

    Mat mean_I_g(hei,wid,CV_32F);

    Mat mean_I_r(hei,wid,CV_32F);

    Mat mean_p(hei,wid,CV_32F);

    Mat Ip_b(hei,wid,CV_32F);

    Mat Ip_g(hei,wid,CV_32F);

    Mat Ip_r(hei,wid,CV_32F);

    Mat mean_Ip_b(hei,wid,CV_32F);

    Mat mean_Ip_g(hei,wid,CV_32F);

    Mat mean_Ip_r(hei,wid,CV_32F);

    Mat cov_Ip_b(hei,wid,CV_32F);

    Mat cov_Ip_g(hei,wid,CV_32F);

    Mat cov_Ip_r(hei,wid,CV_32F);



    Mat II_bb(hei,wid,CV_32F);

    Mat II_gg(hei,wid,CV_32F);

    Mat II_rr(hei,wid,CV_32F);

    Mat II_bg(hei,wid,CV_32F);

    Mat II_br(hei,wid,CV_32F);

    Mat II_gr(hei,wid,CV_32F);



    Mat var_I_bb(hei,wid,CV_32F);

    Mat var_I_gg(hei,wid,CV_32F);

    Mat var_I_rr(hei,wid,CV_32F);

    Mat var_I_bg(hei,wid,CV_32F);

    Mat var_I_br(hei,wid,CV_32F);

    Mat var_I_gr(hei,wid,CV_32F);



    Mat layb;

    Mat layg;

    Mat layr;

    Mat P_32;

    std::vector<Mat> planes;

    split(Img,planes);

    layb=planes[0];

    layg=planes[1];

    layr=planes[2];

    layb.convertTo(layb, CV_32F);

    layg.convertTo(layg, CV_32F);

    layr.convertTo(layr, CV_32F);

    p.convertTo(P_32,CV_32F);

    Mat nom_255(hei,wid,CV_32F,Scalar(255));

    layb=layb.mul(1/nom_255,1);

    layg=layg.mul(1/nom_255,1);

    layr=layr.mul(1/nom_255,1);

    P_32=P_32.mul(1/nom_255,1);

    Mat mean_I_g_temp=boxfilter(layg,r);

    Mat mean_I_r_temp=boxfilter(layr,r);

    Mat mean_p_temp=boxfilter(P_32,r);

    mean_I_g=mean_I_g_temp.mul(1/N,1);

    mean_I_r=mean_I_r_temp.mul(1/N,1);

    mean_p=mean_p_temp.mul(1/N,1);

    Ip_g=layg.mul(P_32,1);

    Ip_r=layr.mul(P_32,1);

    Mat mean_Ip_b_temp=boxfilter(Ip_b,r);

    Mat mean_Ip_g_temp=boxfilter(Ip_g,r);

    Mat mean_Ip_r_temp=boxfilter(Ip_r,r);


    mean_Ip_b=mean_Ip_b_temp.mul(1/N,1);

    mean_Ip_g=mean_Ip_g_temp.mul(1/N,1);

    mean_Ip_r=mean_Ip_r_temp.mul(1/N,1);



    cov_Ip_b=mean_Ip_b-mean_I_b.mul(mean_p,1);

    cov_Ip_g=mean_Ip_g-mean_I_g.mul(mean_p,1);

    cov_Ip_r=mean_Ip_r-mean_I_r.mul(mean_p,1);

    II_bb=layb.mul(layb,1);

    II_gg=layg.mul(layg,1);

    II_rr=layr.mul(layr,1);

    II_bg=layb.mul(layg,1);

    II_br=layb.mul(layr,1);

    II_gr=layg.mul(layr,1);



    Mat bb_box=boxfilter(II_bb,r);

    Mat gg_box=boxfilter(II_gg,r);

    Mat rr_box=boxfilter(II_rr,r);

    Mat bg_box=boxfilter(II_bg,r);

    Mat br_box=boxfilter(II_br,r);

    Mat gr_box=boxfilter(II_gr,r);



    var_I_bb=bb_box.mul(1/N,1)-mean_I_b.mul(mean_I_b);

    var_I_gg=gg_box.mul(1/N,1)-mean_I_g.mul(mean_I_g);

    var_I_rr=rr_box.mul(1/N,1)-mean_I_r.mul(mean_I_r);

    var_I_bg=bg_box.mul(1/N,1)-mean_I_b.mul(mean_I_g);

    var_I_br=br_box.mul(1/N,1)-mean_I_b.mul(mean_I_r);

    var_I_gr=gr_box.mul(1/N,1)-mean_I_g.mul(mean_I_r);



    Mat a_b(hei,wid,CV_32F);

    Mat a_g(hei,wid,CV_32F);

    Mat a_r(hei,wid,CV_32F);



    Mat b(hei,wid,CV_32F);

    Mat sigma(3,3,CV_32F,Scalar(0));

    Mat inv_sigma(3,3,CV_32F);



    for(int j=0;j<hei;j++){

        for(int i=0;i<wid;i++){

            sigma.at<float>(0,0)=var_I_rr.at<float>(j,i)+epsi;

            sigma.at<float>(0,1)=var_I_gr.at<float>(j,i);

            sigma.at<float>(0,2)=var_I_br.at<float>(j,i);

            sigma.at<float>(1,0)=var_I_gr.at<float>(j,i);

            sigma.at<float>(2,0)=var_I_br.at<float>(j,i);

            sigma.at<float>(1,1)=var_I_gg.at<float>(j,i)+epsi;

            sigma.at<float>(2,2)=var_I_bb.at<float>(j,i)+epsi;

            sigma.at<float>(1,2)=var_I_bg.at<float>(j,i);

            sigma.at<float>(2,1)=var_I_bg.at<float>(j,i);

            inv_sigma=sigma.inv(DECOMP_LU);



            a_r.at<float>(j,i)=cov_Ip_r.at<float>(j,i)*inv_sigma.at<float>(0,0)+

                               cov_Ip_g.at<float>(j,i)*inv_sigma.at<float>(1,0)+

                               cov_Ip_b.at<float>(j,i)*inv_sigma.at<float>(2,0);

            a_g.at<float>(j,i)=cov_Ip_r.at<float>(j,i)*inv_sigma.at<float>(0,1)+

                               cov_Ip_g.at<float>(j,i)*inv_sigma.at<float>(1,1)+

                               cov_Ip_b.at<float>(j,i)*inv_sigma.at<float>(2,1);

            a_b.at<float>(j,i)=cov_Ip_r.at<float>(j,i)*inv_sigma.at<float>(0,2)+

                               cov_Ip_g.at<float>(j,i)*inv_sigma.at<float>(1,2)+

                               cov_Ip_b.at<float>(j,i)*inv_sigma.at<float>(2,2);
        }
    }

    b=mean_p-a_b.mul(mean_I_b,1)-a_g.mul(mean_I_g,1)-a_r.mul(mean_I_r,1);

    Mat box_ab=boxfilter(a_b,r);

    Mat box_ag=boxfilter(a_g,r);

    Mat box_ar=boxfilter(a_r,r);

    Mat box_b=boxfilter(b,r);

    Mat q(hei,wid,CV_32F);

    q=box_ab.mul(layb,1)+box_ag.mul(layg,1)+box_ar.mul(layr,1)+box_b;

    q=q.mul(1/N,1);

    return q;
}
bool Gamma(Mat& img, float gamma, float gain)
{
	if (gamma <= 0.0f) return false;

	double dinvgamma = 1/gamma*gain;
	double dMax = pow(255.0, dinvgamma) / 255.0;
	int dim(256);  
	Mat lut(1,&dim,CV_8U);  
	for(int i = 0; i < 256; i++)  
	{  
		lut.at<uchar>(i) = saturate_cast<uchar>( pow((double)i, dinvgamma) / dMax);
	}  

	LUT(img, lut, img);

	return true;
}
extern "C" {

JNIEXPORT void JNICALL Java_easyimage_slrcamera_ImageProcessX_AutoDehaze(JNIEnv* env, jobject thiz, jobject bitmap, float ratio)
{
    LOGE("Enter: HDR");
    AndroidBitmapInfo  info;
    void*              pixels;
    int                ret;

    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        return;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        return;
    }
    Mat img(info.height, info.width, CV_8UC4, (unsigned char*)pixels);
    LOGE("###############   w=%d, h=%d", info.width, info.height);
    Mat tmp;
    cvtColor(img, tmp, CV_RGBA2BGR); 
    process(tmp);
    Gamma(tmp, 1.1, 1.0);
    cvtColor(tmp, img, CV_BGR2RGBA);
    AndroidBitmap_unlockPixels(env, bitmap);
}

int cmp ( const void *a , const void *b )
{
    return *(uchar *)a > *(uchar *)b ? 1 : -1;
}

JNIEXPORT jfloat JNICALL Java_easyimage_slrcamera_ImageProcessX_GetHazeValue(JNIEnv* env, jobject thiz, jobject bitmap)
{
    AndroidBitmapInfo  info;
    void*              pixels;
    int                ret;
    if ((ret = AndroidBitmap_getInfo(env, bitmap, &info)) < 0) {
        return -1;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return -1;
    }

    if ((ret = AndroidBitmap_lockPixels(env, bitmap, &pixels)) < 0) {
        return -1;
    }
    uchar* data = (uchar*)pixels;
    int w = info.width;
    int h = info.height;
    float d = 0;
    int block = max(w,h)/72;
    if(block < 3) block = 3;
    int count = 0;
    uchar* tmp = new uchar[block*block];
    for(int y = 0; y < h/block*block; y+=block)
    {
        for(int x = 0; x < w/block*block; x+=block)
        {   
            for( int i = 0; i < block; i++)
            {
                for(int j = 0; j < block; j++)
                {
                   uchar* c = data + ((y+i)*w + x+j)*4; 
                   tmp[i*block+j] = min(c[0], min(c[1], c[2])); 
		}
            }
            qsort(tmp, block*block, sizeof(uchar), cmp);
	    d += tmp[0]; count ++;
          #if 0
            for( int i = 0; i < block; i++)
            {
                for(int j = 0; j < block; j++)
                {
                   uchar* c = data + ((y+i)*w + x+j)*4;
                   c[0] = c[1] = c[2] = tmp[0];
                }
            }
	 #endif
        }
    }
    delete tmp; 
    AndroidBitmap_unlockPixels(env, bitmap);
    return d/count;
}
}

