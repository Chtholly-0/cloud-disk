package com.blackcat.common.config.template;

import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import com.aliyun.teaopenapi.models.Config;


public class SmsTemplate {
    public  static void SendSms(String mobile, String code)
    {
        try {
            Config config = new Config()
                    // 您的AccessKey ID
                    .setAccessKeyId("LTAI5tCkyCK4WMPtyhvHP9zs")
                    // 您的AccessKey Secret
                    .setAccessKeySecret("nKDJcFgQm6Vt2UGcMqWcTJR6HPwHMm");
            // 访问的域名
            config.endpoint = "dysmsapi.aliyuncs.com";
            com.aliyun.dysmsapi20170525.Client client =new com.aliyun.dysmsapi20170525.Client(config);
            SendSmsRequest sendSmsRequest = new SendSmsRequest()
                    .setSignName("阿里云短信测试")
                    .setTemplateCode("SMS_154950909")
                    .setPhoneNumbers(mobile)
                    .setTemplateParam("{\"code\":\""+code+"\"}");
            // 复制代码运行请自行打印 API 的返回值
            SendSmsResponse response=client.sendSms(sendSmsRequest);
            SendSmsResponseBody body=response.getBody();
            System.out.println(body.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
