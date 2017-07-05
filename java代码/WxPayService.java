package com.yunda.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

/**
 * 微信支付
 *
 */
public class WxPayService {
	private String appId;//AppID(应用ID)
	private String secret;//密钥
	private String partnerId;//商户平台登录帐号
	private String partnerKey;//商户秘钥
	/**
	 * 该接口调用“统一下单”接口，并拼装JSSDK发起支付请求需要的参数
	 * 详见http://mp.weixin.qq.com/wiki/7/aaa137b55fb2e0456bf8dd9148dd613f.html
	 * @param openId 支付人openId
	 * @param outTradeNo 商户端对应订单号
	 * @param amt 金额(单位元)
	 * @param body 商品描述
	 * @param tradeType 交易类型 JSAPI，NATIVE，APP，WAP
	 * @param ip 发起支付的客户端IP
	 * @param notifyUrl 通知地址
	 * @return
	 */
	public Map<String, String> getJSSDKPayInfo(String openId, String outTradeNo, double amt, String body, String ip, String callbackUrl) {
		Map<String, String> packageParams = new HashMap<String, String>();
		packageParams.put("appid", getAppId());
		packageParams.put("mch_id",getPartnerId());
		packageParams.put("body", body);
		packageParams.put("out_trade_no", outTradeNo);
		packageParams.put("total_fee", (int) (amt * 100) + "");
		packageParams.put("spbill_create_ip", ip);
		packageParams.put("notify_url", callbackUrl);
		packageParams.put("trade_type", "JSAPI");
		packageParams.put("openid", openId);
		
		return getJSSDKPayInfo(packageParams);
	}
	/**
	 * js支付
	 */
	public Map<String, String> getJSSDKPayInfo(Map<String, String> parameters) {
		Map<String, String> result = getPrepayId(parameters);
		String prepayId = result.get("prepay_id");
		if (prepayId == null || prepayId.equals("")) {
			return result;
			//throw new RuntimeException(String.format("Failed to get prepay_id due to error code '%s'(%s).", result.get("err_code"), result.get("return_msg")));
		}
		
		Map<String, String> payInfo = new HashMap<String, String>();
		payInfo.put("appId", getAppId());
		// 支付签名时间戳，注意微信jssdk中的所有使用timestamp字段均为小写。但最新版的支付后台生成签名使用的timeStamp字段名需大写其中的S字符
		payInfo.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
		payInfo.put("nonceStr", System.currentTimeMillis() + "");
		payInfo.put("package", "prepay_id=" + prepayId);
		payInfo.put("signType", "MD5");
		
		String finalSign = createSign(payInfo, getPartnerKey());
		payInfo.put("paySign", finalSign);
		return payInfo;
	}
	
	/**
	 * 统一下单(详见http://pay.weixin.qq.com/wiki/doc/api/jsapi.php?chapter=9_1)
     * 在发起微信支付前，需要调用统一下单接口，获取"预支付交易会话标识"
     * 
     * @param parameters
	 */
	public Map<String, String> getPrepayId(final Map<String, String> parameters) {
	    String nonce_str = System.currentTimeMillis() + "";

	    final SortedMap<String, String> packageParams = new TreeMap<String, String>(parameters);
	    packageParams.put("appid", getAppId());
	    packageParams.put("mch_id", getPartnerId());
	    packageParams.put("nonce_str", nonce_str);
	    checkParameters(packageParams);
	    
	    String sign = createSign(packageParams, getPartnerKey());
	    packageParams.put("sign", sign);
	    
	    StringBuilder request = new StringBuilder("<xml>");
	    for (Entry<String, String> para : packageParams.entrySet()) {
	    	request.append(String.format("<%s>%s</%s>", para.getKey(), para.getValue(), para.getKey()));
	    }
	    request.append("</xml>");
	    
	    HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/pay/unifiedorder");
	    
	    StringEntity entity = new StringEntity(request.toString(), Consts.UTF_8);
	    httpPost.setEntity(entity);
	    try {
	    	CloseableHttpResponse response = getHttpclient().execute(httpPost);
	    	final StatusLine statusLine = response.getStatusLine();
	        final HttpEntity httpEntity = response.getEntity();
	        if (statusLine.getStatusCode() >= 300) {
	        	throw new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase());
	        }
	        String responseContent = httpEntity == null ? null : EntityUtils.toString(httpEntity, Consts.UTF_8);
	    	return prepayIdResult(responseContent);
	    } catch (IOException e) {
	    	throw new RuntimeException("Failed to get prepay id due to IO exception.", e);
	    }
	 }
	protected CloseableHttpClient getHttpclient() {
		HttpClientBuilder httbuilder = HttpClients.custom();
		return  httbuilder.build();
	}
	final String[] REQUIRED_PARAMETERS = { "appid", "mch_id", "body", "out_trade_no", "total_fee", "spbill_create_ip", "notify_url","trade_type" };
	//检查参数
	private void checkParameters(Map<String, String> parameters) {
		for (String para : REQUIRED_PARAMETERS) {
			if (!parameters.containsKey(para))
				throw new IllegalArgumentException("Reqiured argument '" + para + "' is missing.");
		}
		if ("JSAPI".equals(parameters.get("trade_type")) && !parameters.containsKey("openid"))
			throw new IllegalArgumentException("Reqiured argument 'openid' is missing when trade_type is 'JSAPI'.");
	}
	/**
     * 微信公众号支付签名算法(详见:http://pay.weixin.qq.com/wiki/doc/api/index.php?chapter=4_3)
     * @param packageParams 原始参数
     * @param signKey 加密Key(即 商户Key)
     * @param charset 编码
     * @return 签名字符串
     */
    public static String createSign(Map<String, String> packageParams, String signKey) {
        List<String> keys = new ArrayList<String>(packageParams.keySet());
        Collections.sort(keys);

        StringBuffer toSign = new StringBuffer();
        for (String key : keys) {
            String value = packageParams.get(key);
            if (null != value && !"".equals(value) && !"sign".equals(key) && !"key".equals(key)) {
                toSign.append(key + "=" + value + "&");
            }
        }
        toSign.append("key=" + signKey);
        String sign = DigestUtils.md5Hex(toSign.toString()).toUpperCase();
        return sign;
    }
    /**
     * 解析接口返回XML结果
     */
    private Map<String, String> prepayIdResult(String result) {
    	Map<String, String> map = new HashMap<String, String>();
		try {
			Document document = DocumentHelper.parseText(result);
			Element root = document.getRootElement();
			// 通过element对象的elementIterator方法获取迭代器
			Iterator<?> it = root.elementIterator();
			while (it.hasNext()) {
				Element element = (Element) it.next();
				map.put(element.getName(), element.getTextTrim());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return map;
    }
    public WxPayService(){
    	
    }
    public WxPayService(String appId,String secret,String partnerId,String partnerKey){
    	this.appId = appId;
    	this.secret = secret;
    	this.partnerId = partnerId;
    	this.partnerKey = partnerKey;
    }
	public String getAppId() {
		return appId;
	}
	public void setAppId(String appId) {
		this.appId = appId;
	}
	public String getSecret() {
		return secret;
	}
	public void setSecret(String secret) {
		this.secret = secret;
	}
	public String getPartnerId() {
		return partnerId;
	}
	public void setPartnerId(String partnerId) {
		this.partnerId = partnerId;
	}
	public String getPartnerKey() {
		return partnerKey;
	}
	public void setPartnerKey(String partnerKey) {
		this.partnerKey = partnerKey;
	}
}
