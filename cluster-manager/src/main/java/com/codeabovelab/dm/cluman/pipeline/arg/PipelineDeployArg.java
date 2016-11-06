/*
 * Copyright 2016 Code Above Lab LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codeabovelab.dm.cluman.pipeline.arg;

import com.codeabovelab.dm.cluman.cluster.docker.management.argument.CreateContainerArg;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PipelineDeployArg {

    private final String pipelineInstance;
    //    private final String registry;
//    private final String imageId;
    private final String stage;
    private final String comment;
    private final Map<String, String> arguments;
    private final CreateContainerArg createContainerArg;

}