/*
 * Copyright (c) 2017 Nike Inc.
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

package com.nike.cerberus

import com.nike.cerberus.util.EnvVarUtils
import org.junit.Before
import org.junit.Test

class LambdaIntegrationTest {

    private CerberusCrossRegionBackupHandler handler;

    @Before
    public void before() {
        handler = new CerberusCrossRegionBackupHandler(String.format("arn:aws:iam::%s:role/%s",
                EnvVarUtils.getRequiredEnvVar(CerberusCrossRegionBackupHandler.ACCOUNT_ID_ENV_VAR_KEY),
                EnvVarUtils.getRequiredEnvVar(CerberusCrossRegionBackupHandler.ROLE_NAME_ENV_VAR_KEY)))
    }

    @Test
    public void test_lambda() {
        handler.handle()
    }

}
