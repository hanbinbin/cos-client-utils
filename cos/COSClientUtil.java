package com.gomore.patrol.cos;

import android.util.Base64;
import android.util.Log;

import com.gomore.patrol.PatrolApplication;
import com.gomore.patrol.common.utils.HMACSHA1Util;
import com.gomore.patrol.service.UploadFileSuccessFailListener;
import com.tencent.cos.COSClient;
import com.tencent.cos.COSClientConfig;
import com.tencent.cos.common.COSEndPoint;
import com.tencent.cos.model.COSRequest;
import com.tencent.cos.model.COSResult;
import com.tencent.cos.model.PutObjectRequest;
import com.tencent.cos.model.PutObjectResult;
import com.tencent.cos.task.listener.IUploadTaskListener;

/**
 * Created by asus on 2017/2/6.
 */
public class COSClientUtil {
    private final String persistenceId = "patrol_qupload";
    private UploadFileSuccessFailListener uploadFileSuccessFailListener;
    protected String bucket;
    protected String appId;
    protected String secretId;
    protected String secretKey;
    protected String endPoint;

    private COSClient cosClient;
    private static COSClientUtil instance;

    public static COSClientUtil getInstance() {
        if (instance == null) {
            instance = new COSClientUtil();
        }
        return instance;
    }

    private COSClientUtil() {
    }

    public void setUploadFileSuccessFailListener(UploadFileSuccessFailListener uploadFileSuccessFailListener) {
        this.uploadFileSuccessFailListener = uploadFileSuccessFailListener;
    }

    /**
     * 清空客户端缓存。
     * 一般在用户注销时， 执行。
     */
    public void clear() {
        cosClient = null;
    }

    /**
     * 配置客户端。
     */
    private void ensureConfig() {
        if (cosClient == null) {
            COSClientConfig config = new COSClientConfig();
            COSEndPoint cosEndPoint = COSEndPoint.COS_SH;
            config.setEndPoint(cosEndPoint);
            config.setConnectionTimeout(60 * 1000); // 连接超时，默认15秒
            config.setSocketTimeout(60 * 1000); // socket超时，默认15秒
            config.setMaxConnectionsCount(1); // 最大并发请求书，
            config.setMaxRetryCount(2); // 失败后最大重试次数，
            bucket = "patrol";
            appId = "1253341783";
            secretId = "AKIDdFlATio5R4KXZSnh3EVhleTIxWvFDbbZ";
            secretKey = "24ESbdejiWCa1kcPFHUsR9006cG3z6Oq";
            endPoint = "patrol-1253341783.cossh.myqcloud.com";
            cosClient = new COSClient(PatrolApplication.getInstance(), appId, config, persistenceId);
        }
    }

    /**
     * 上传文件
     *
     * @param objectKey
     * @param localFilePath
     */
    public PutObjectResult upload(String objectKey, String localFilePath) {
        //初始化CosClient对象
        ensureConfig();

        String sign = getSign();
        PutObjectRequest putObjectRequest = new PutObjectRequest();
        putObjectRequest.setBucket(bucket);
        putObjectRequest.setCosPath(objectKey);
        putObjectRequest.setSrcPath(localFilePath);
        putObjectRequest.setSign(sign);
        putObjectRequest.setListener(new IUploadTaskListener() {
            @Override
            public void onSuccess(COSRequest cosRequest, COSResult cosResult) {

                PutObjectResult result = (PutObjectResult) cosResult;
                if (result != null) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(" 上传结果： ret=" + result.code + "; msg =" + result.msg + "\n");
                    stringBuilder.append(" access_url= " + result.access_url == null ? "null" : result.access_url + "\n");
                    stringBuilder.append(" resource_path= " + result.resource_path == null ? "null" : result.resource_path + "\n");
                    stringBuilder.append(" url= " + result.url == null ? "null" : result.url);
                    Log.w("TEST", stringBuilder.toString());
                    uploadFileSuccessFailListener.onUploadSucess("success");
                }
            }

            @Override
            public void onFailed(COSRequest COSRequest, final COSResult cosResult) {
                Log.w("TEST", "上传出错： ret =" + cosResult.code + "; msg =" + cosResult.msg);
                uploadFileSuccessFailListener.onUploadFailure();
            }

            @Override
            public void onProgress(COSRequest cosRequest, final long currentSize, final long totalSize) {
                float progress = (float) currentSize / totalSize;
                progress = progress * 100;
                Log.w("TEST", "进度" + progress);
            }

            @Override
            public void onCancel(COSRequest cosRequest, COSResult cosResult) {

            }
        });
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        return putObjectResult;
    }


    /**
     * 访问COS文件的基准地址。
     *
     * @return
     */
    public String getBaseUrl() {
        return "http://" + endPoint + "/";
    }

    /**
     * 取得远程文件地址.
     *
     * @param objectKey
     * @return
     */
    public String getRemoteFileUrl(String objectKey) {
        return getBaseUrl() + objectKey;
    }

    /**
     * 获取多次签名
     *
     * @return
     */
    public String getSign() {
        String sign = null;
        long time = System.currentTimeMillis();
        long currentTimeSecond = time / 1000;
        long expired = currentTimeSecond + 3600;
        int random = (int) (Math.random() * (999999999 - 100000000 + 1) + 100000000);
        String original = "a=" + appId + "&b=" + bucket +
                "&k=" + secretId + "&e=" + expired + "&t=" + currentTimeSecond + "&r=" + random + "&f=";
        Log.e("original", original);
        byte[] add = null;
        byte[] signTmpByte = null;
        try {
            signTmpByte = HMACSHA1Util.HmacSHA1Encrypt(original, secretKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] originalByte = original.getBytes();
        add = HMACSHA1Util.byteMerger(signTmpByte, originalByte);
        sign = Base64.encodeToString(add, Base64.DEFAULT);
        //base64加密后的字符有换行现象，然后在网上搜索，发现base64一行不能超过76字符，超过则添加回车换行符
        sign = sign.replaceAll("[\\s*\t\n\r]", "");
        Log.e("sign", sign);
        return sign;
    }
}
