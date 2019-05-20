/*
 * Copyright (c) 2019 Nike Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nike.cerberus.lambda.waf;

import com.github.seratch.jslack.api.model.block.DividerBlock;
import com.github.seratch.jslack.api.model.block.LayoutBlock;
import com.github.seratch.jslack.api.model.block.SectionBlock;
import com.github.seratch.jslack.api.model.block.composition.MarkdownTextObject;

import java.util.Arrays;
import java.util.List;

public class SlackMessageTextBuilder {

    public List<LayoutBlock> generateMessageBlocks(SlackMessageText messageText) {
        return Arrays.asList(

                SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(String.format("*Principal Name*\n%s", messageText.getPrincipalName()))
                                .build())
                        .build(),

                SectionBlock.builder()
                        .text(MarkdownTextObject.builder()
                                .text(String.format("*Action*\n%s", messageText.getAction()))
                                .build())
                        .build(),

                SectionBlock.builder()
                        .fields(Arrays.asList(MarkdownTextObject.builder()
                                        .text(String.format("*SDB Name*\n%s", messageText.getSdbName()))
                                        .build(),
                                MarkdownTextObject.builder()
                                        .text(String.format("*Client Version*\n%s", messageText.getClientVersion()))
                                        .build()))
                        .build(),

                SectionBlock.builder()
                        .fields(Arrays.asList(MarkdownTextObject.builder()
                                        .text(String.format("*Owner*\n%s", messageText.getOwner()))
                                        .build(),
                                MarkdownTextObject.builder()
                                        .text(String.format("*Count*\n%s", messageText.getCount()))
                                        .build()))
                        .build(),

                new DividerBlock()
        );
    }
}