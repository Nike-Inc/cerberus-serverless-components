package com.nike.cerberus.lambda.waf.processor;

import com.fieldju.slackclient.Message;
import com.fieldju.slackclient.SlackClient;
import com.nike.cerberus.lambda.waf.CloudFrontLogHandlerConfig;
import org.apache.commons.lang3.StringUtils;

public class SlackUtils {

    public static void logMsgIfEnabled(String msg, CloudFrontLogHandlerConfig config, String username) {

        if (StringUtils.isNotBlank(config.getSlackWebHookUrl())) {
            Message.Builder msgBuilder = new Message.Builder(msg).userName(username);
            if (StringUtils.startsWith(config.getSlackIcon(), "http")) {
                msgBuilder.iconUrl(config.getSlackIcon());
            } else {
                msgBuilder.iconEmoji(config.getSlackIcon());
            }
            new SlackClient(config.getSlackWebHookUrl()).sendMessage(msgBuilder.build());
        }
    }

}
