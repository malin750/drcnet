package com.drcnet.platformsdk;


import com.alibaba.fastjson.JSONObject;
import org.apache.http.*;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: ml
 * @CreateTime: 2020/5/29
 * @Description:调用API工具包
 */
public class APIUtil {

    private static final String URL = "http://open.drcdaas.com/gateway/transfer/datasetInvoking/api";
    private static URIBuilder uriBuilder;
    private static CloseableHttpClient httpClient;

    private APIUtil() {
    }

    static {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        //最大连接数100
        cm.setMaxTotal(100);
        //单个路由默认最大连接数20
        cm.setDefaultMaxPerRoute(20);
        //从连接池中获取client对象，多例,重试链接次数5次
        httpClient = HttpClients.custom().setConnectionManager(cm).setRetryHandler(retryHandler(5)).build();

        /**
         * 清理空闲链接
         */
        IdleConnectionEvictor idleConnectionEvictor = new IdleConnectionEvictor(cm);
        Thread connectionEvictorThread = new Thread(idleConnectionEvictor);
        connectionEvictorThread.start();
        try {
            uriBuilder = new URIBuilder(URL);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    /**
     * 调用api的公共方法
     *
     * @param appId   应用id
     * @param dataId  api id
     * @param version api版本号
     * @param sign    数字签名
     * @param param   应用级请求参数
     * @return
     * @throws URISyntaxException
     * @throws IOException
     */
    public static HttpEntity callAPI(long appId, long dataId, String version, String sign, JSONObject param) throws URISyntaxException, IOException {
        List<NameValuePair> nameValuePairs = new ArrayList<>(0);
        NameValuePair appIdNameValuePair = new NameValuePair() {
            @Override
            public String getName() {
                return "appId";
            }

            @Override
            public String getValue() {
                return appId + "";
            }
        };
        NameValuePair dataIdNameValuePair = new NameValuePair() {
            @Override
            public String getName() {
                return "dataId";
            }

            @Override
            public String getValue() {
                return dataId + "";
            }
        };
        NameValuePair versionNameValuePair = new NameValuePair() {
            @Override
            public String getName() {
                return "version";
            }

            @Override
            public String getValue() {
                return version;
            }
        };
        NameValuePair signNameValuePair = new NameValuePair() {
            @Override
            public String getName() {
                return "sign";
            }

            @Override
            public String getValue() {
                return sign;
            }
        };
        nameValuePairs.add(appIdNameValuePair);
        nameValuePairs.add(dataIdNameValuePair);
        nameValuePairs.add(versionNameValuePair);
        nameValuePairs.add(signNameValuePair);

        uriBuilder.setParameters(nameValuePairs);

        HttpRequestBase requestBase = createRequest(param);
        CloseableHttpResponse response = null;
        response = httpClient.execute(requestBase);
        // 判断返回状态是否为200
        if (response.getStatusLine().getStatusCode() == 200) {
            return response.getEntity();
        }
        return null;
    }


    /**
     * @Description: 封装请求
     * @param: [url, method, param]
     * @return: org.apache.http.client.methods.HttpRequestBase
     * @Author: ml
     * @Date: 2020/6/1
     */

    private static HttpRequestBase createRequest(JSONObject param) throws URISyntaxException {
        HttpRequestBase requestBase = new HttpPost(uriBuilder.build());
        StringEntity stringEntity = (new StringEntity(JSONObject.toJSONString(param), "UTF-8"));
        stringEntity.setContentEncoding("UTF-8");
        stringEntity.setContentType("application/json");
        ((HttpEntityEnclosingRequestBase) requestBase).setEntity(stringEntity);

        return requestBase;
    }

    /**
     * 请求重试处理
     *
     * @param tryTimes 重试次数
     * @return
     */
    private static HttpRequestRetryHandler retryHandler(final int tryTimes) {

        HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                // 如果已经重试了n次，就放弃
                if (executionCount >= tryTimes) {
                    return false;
                }
                // 如果服务器丢掉了连接，那么就重试
                if (exception instanceof NoHttpResponseException) {
                    return true;
                }
                // 不要重试SSL握手异常
                if (exception instanceof SSLHandshakeException) {
                    return false;
                }
                // 超时
                if (exception instanceof InterruptedIOException) {
                    return false;
                }
                // 目标服务器不可达
                if (exception instanceof UnknownHostException) {
                    return true;
                }
                // 连接被拒绝
                if (exception instanceof ConnectTimeoutException) {
                    return false;
                }
                // SSL握手异常
                if (exception instanceof SSLException) {
                    return false;
                }
                HttpClientContext clientContext = HttpClientContext.adapt(context);
                HttpRequest request = clientContext.getRequest();
                // 如果请求是幂等的，就再次尝试
                if (!(request instanceof HttpEntityEnclosingRequest)) {
                    return true;
                }
                return false;
            }
        };
        return httpRequestRetryHandler;
    }

    /**
     * 监听连接池中空闲连接，清理无效连接
     */
    private static class IdleConnectionEvictor implements Runnable {

        private final HttpClientConnectionManager connectionManager;

        private volatile boolean shutdown;

        public IdleConnectionEvictor(HttpClientConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        //3s检查一次
                        wait(3000);
                        // 关闭失效的连接
                        connectionManager.closeExpiredConnections();
                    }
                }
            } catch (InterruptedException ex) {
                // 结束
                ex.printStackTrace();
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }

    public static void main(String[] args) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("appId", 7811589334259271L);
        jsonObject.put("dataId", 12L);
        jsonObject.put("timestamp", "20200601162222");
        jsonObject.put("name", "布期末库存比年初增减");
        jsonObject.put("dbCode", "hgjd");

        try {
            String sign = SignUtil.createSign(jsonObject, "EAUJ6CIKPBMCNW36");
            HttpEntity httpEntity = callAPI(7811589334259271L,12L,"20200601162222",sign,jsonObject);
            JSONObject res = JSONObject.parseObject(EntityUtils.toString(httpEntity));
            Object body = res.get("body");
            String resSign = (String)res.get("sign");
            if(SignUtil.signVerify(body,resSign,"EAUJ6CIKPBMCNW36")){
                System.out.println("ok");
            }
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
    }
}
