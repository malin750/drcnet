package com.drcnet.platformsdk;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * @Author: ml
 * @CreateTime: 2020/5/29
 * @Description:数字签名工具包
 */
public class SignUtil {
    private SignUtil() {

    }

    /**
     * 生成数字签名
     *
     * @param params    请求参数, 无请求参数的情况下，params传null
     * @param secretKey 密钥
     * @return sign
     * @throws UnsupportedEncodingException e
     */
    public static String createSign(Object params, String secretKey) throws UnsupportedEncodingException {
        if (params instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) params;
            return createSignJSONObject(jsonObject, secretKey);
        } else if (params instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) params;
            return createSignJSONArray(jsonArray, secretKey);
        }
        return createSignJSONObject(null, secretKey);
    }


    /**
     * 验证数字签名
     *
     * @param body    提取返回结果的body进行验签
     * @param sign      签名
     * @param secretKey 密钥
     * @return
     */
    public static boolean signVerify(Object body, String sign, String secretKey) throws UnsupportedEncodingException {
        if (body instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) body;
            return signVerifyJSONObject(jsonObject, sign, secretKey);
        } else if (body instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) body;
            return signVerifyJSONArray(jsonArray, sign, secretKey);
        }
        /**
         * 返回数据为其他类型时，只验证秘钥
         */
        return signVerifyJSONObject(null, sign, secretKey);
    }

    /**
     * 验证JSONObject类型的数字签名
     *
     * @param params    参数
     * @param sign      签名
     * @param secretKey 密钥
     * @return
     */
    private static boolean signVerifyJSONObject(Map<String, Object> params, String sign, String secretKey) throws UnsupportedEncodingException {
        String newSign = createSignJSONObject(params, secretKey);
        return Objects.equals(newSign, sign);
    }

    /**
     * 验证JSONArray类型的数字签名
     *
     * @param params    参数
     * @param sign      签名
     * @param secretKey 密钥
     * @return
     */
    private static boolean signVerifyJSONArray(JSONArray params, String sign, String secretKey) throws UnsupportedEncodingException {
        String newSign = createSignJSONArray(params, secretKey);
        return Objects.equals(newSign, sign);
    }


    /**
     * 生成签名 map
     *
     * @param params    参数,类型可以是map类型，也可以是com.alibaba.fastjson.JSONObject类型
     * @param secretKey 密钥
     * @return sign
     * @throws UnsupportedEncodingException e
     */
    private static String createSignJSONObject(Map<String, Object> params, String secretKey) throws UnsupportedEncodingException {
        String s = parameterText(params, "", null) + secretKey;
        return DigestUtils.md5Hex(s.getBytes("UTF-8"));
    }

    /**
     * 生成签名 om.alibaba.fastjson.JSONArray
     *
     * @param array     json数组
     * @param secretKey 密钥
     * @return sign
     * @throws UnsupportedEncodingException
     */
    private static String createSignJSONArray(JSONArray array, String secretKey) throws UnsupportedEncodingException {
        HashMap<String, JSONArray> map = new HashMap<>();
        map.put("array", array);
        String s = parameterText(map, "", null) + secretKey;
        return DigestUtils.md5Hex(s.getBytes("UTF-8"));
    }

    /**
     * 把数组所有元素排序，并按照“参数参数值”的模式用“@param separator”字符拼接成字符串
     *
     * @param parameters 参数
     * @param separator  分隔符
     * @param ignoreKey  需要忽略添加的key
     * @return 去掉空值与签名参数后的新签名，拼接后字符串
     */
    private static String parameterText(Map parameters, String separator, String... ignoreKey) {
        if (parameters == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        if (null != ignoreKey) {
            Arrays.sort(ignoreKey);
        }
        // 已经排序好处理
        if (parameters instanceof SortedMap) {
            for (Map.Entry<String, Object> entry : (Set<Map.Entry<String, Object>>) parameters.entrySet()) {
                Object v = entry.getValue();
                if (null == v || "".equals(v.toString().trim()) || (null != ignoreKey && Arrays.binarySearch(ignoreKey, entry.getKey()) >= 0)) {
                    continue;
                }
                sb.append(entry.getKey()).append(v.toString().trim()).append(separator);
            }
            return sb.toString();

        }
        // 未排序须处理
        List<String> keys = new ArrayList<String>(parameters.keySet());
        //排序
        Collections.sort(keys);
        for (String k : keys) {
            String valueStr = "";
            Object o = parameters.get(k);
            if (null == o) {
                continue;
            }
            if (o instanceof String[]) {
                String[] values = (String[]) o;

                for (int i = 0; i < values.length; i++) {
                    String value = values[i].trim();
                    if ("".equals(value)) {
                        continue;
                    }
                    valueStr += (i == values.length - 1) ? value : value + ",";
                }
            } else {
                valueStr = o.toString();
            }
            if (null == valueStr || "".equals(valueStr.trim()) || (null != ignoreKey && Arrays.binarySearch(ignoreKey, k) >= 0)) {
                continue;
            }
            sb.append(k).append(valueStr).append(separator);
        }
        return sb.toString();
    }
}
