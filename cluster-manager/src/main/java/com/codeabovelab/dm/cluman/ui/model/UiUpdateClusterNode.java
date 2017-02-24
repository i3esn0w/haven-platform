/*
 * Copyright 2017 Code Above Lab LLC
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

package com.codeabovelab.dm.cluman.ui.model;

import com.codeabovelab.dm.cluman.cluster.docker.model.UpdateNodeCmd;
import lombok.Data;

import java.util.Map;

/**
 */
@Data
public class UiUpdateClusterNode {
    /**
     * Code from 'node.version'
     */
    private long version;

    private Map<String, String> labels;

    /**
     * Role of the node.
     */
    private UpdateNodeCmd.Role role;

    /**
     * Availability of the node.
     */
    private UpdateNodeCmd.Availability availability;

}
