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

package com.codeabovelab.dm.cluman.source;

import com.codeabovelab.dm.cluman.cluster.docker.model.Mount;
import com.codeabovelab.dm.cluman.cluster.docker.model.swarm.ContainerSpec;
import com.codeabovelab.dm.cluman.cluster.docker.model.swarm.Task;
import com.codeabovelab.dm.cluman.model.ContainerSource;
import com.codeabovelab.dm.cluman.model.ImageName;
import com.codeabovelab.dm.cluman.model.RootSource;
import com.codeabovelab.dm.common.utils.Sugar;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 */
@Slf4j
public class SourceUtil {

    private static final Splitter SP_VOLUMES = Splitter.on(':').limit(3);
    private static final Splitter SP_VOLUMES_OPTS = Splitter.on(',');
    private static final Splitter SP_HOSTS = Splitter.on(CharMatcher.anyOf(" \t"));


    public static void validateSource(RootSource source) {
        Assert.notNull(source, "source is null");
        String version = source.getVersion();
        Assert.isTrue(RootSource.V_1_0.equals(version), "source has unsupported version:" +
          version + " when support: " + RootSource.V_1_0);
    }

    public static void toSource(Task.TaskSpec taskSpec, ContainerSource cs) {
        ContainerSpec conSpec = taskSpec.getContainer();
        hostsToSource(conSpec.getHosts(), cs.getExtraHosts());
        ContainerSpec.DnsConfig dc = conSpec.getDnsConfig();
        if(dc != null) {
            Sugar.setIfNotNull(cs.getDnsSearch()::addAll, dc.getSearch());
            Sugar.setIfNotNull(cs.getDns()::addAll, dc.getServers());
        }
        mountsToSource(conSpec.getMounts(), cs);
        String image = conSpec.getImage();
        ImageName in = ImageName.parse(image);
        cs.setImage(in.getFullName());
        cs.setImageId(in.getId());
        Sugar.setIfNotNull(cs.getLabels()::putAll, conSpec.getLabels());
        Sugar.setIfNotNull(cs.getCommand()::addAll, conSpec.getCommand());
        Sugar.setIfNotNull(cs.getEnvironment()::addAll, conSpec.getEnv());
        cs.setHostname(conSpec.getHostname());
    }

    public static void fromSource(ContainerSource c, Task.TaskSpec.Builder builder) {
        ContainerSpec.Builder csb = ContainerSpec.builder();
        csb.hosts(hostsFromSource(c.getExtraHosts()));
        csb.dnsConfig(ContainerSpec.DnsConfig.builder()
          .servers(c.getDns())
          .search(c.getDnsSearch())
          .build());
        csb.mounts(convertMounts(c));
        csb.image(ImageName.nameWithId(c.getImage(), c.getImageId()))
          .labels(c.getLabels())
          .command(c.getCommand())
          .env(c.getEnvironment())
          .hostname(c.getHostname());
        builder.container(csb.build());
    }

    private static List<Mount> convertMounts(ContainerSource c) {
        List<Mount> res = new ArrayList<>();
        final String volumeDriver = c.getVolumeDriver();
        c.getVolumeBinds().forEach(vb -> {
            Iterator<String> i = SP_VOLUMES.split(vb).iterator();
            Mount.Builder mb = Mount.builder();
            mb.source(i.next());
            mb.target(i.next());
            Mount.VolumeOptions.Builder vob = Mount.VolumeOptions.builder();
            if(i.hasNext()) {
                Mount.BindOptions.Builder bo = Mount.BindOptions.builder();
                for(String opt : SP_VOLUMES_OPTS.split(i.next())) {
                    switch (opt) {
                        case "ro":
                            mb.readonly(true);
                            break;
                        case "rw":
                            mb.readonly(false);
                            break;
                        case "rshared":
                            bo.propagation(Mount.Propagation.RSHARED);
                            break;
                        case "rslave":
                            bo.propagation(Mount.Propagation.RSLAVE);
                            break;
                        case "rprivate":
                            bo.propagation(Mount.Propagation.RPRIVATE);
                            break;
                        case "shared":
                            bo.propagation(Mount.Propagation.SHARED);
                            break;
                        case "slave":
                            bo.propagation(Mount.Propagation.SLAVE);
                            break;
                        case "private":
                            bo.propagation(Mount.Propagation.PRIVATE);
                            break;
                        case "nocopy":
                            vob.noCopy(true);
                            break;
                    }
                }
                mb.bindOptions(bo.build());
            }
            if(volumeDriver != null) {
                vob.driverConfig(Mount.Driver.builder().name(volumeDriver).build());
            }
            mb.volumeOptions(vob.build());
            res.add(mb.build());
        });
        //c.getVolumesFrom() - not supported by services
        return res;
    }


    private static void mountsToSource(List<Mount> mounts, ContainerSource cs) {
        if(mounts == null) {
            return;
        }
        String driver = null;
        for(Mount mount: mounts) {
            Mount.Type type = mount.getType();
            if(type != Mount.Type.BIND && type != Mount.Type.VOLUME) {
                log.warn("Unsupported type: {} of mount {}", type, mount);
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(mount.getSource()).append(':').append(mount.getTarget()).append(':');
            appendOpt(sb, mount.isReadonly()? "ro" : "rw");
            Mount.VolumeOptions vo = mount.getVolumeOptions();
            if(vo != null) {
                Mount.Driver dc = vo.getDriverConfig();
                if(dc != null) {
                    String name = dc.getName();
                    if(name != null) {
                        if(driver != null) {
                            if(!name.equals(driver)) {
                                log.error("Unsupported, different volume drivers: {} and {} in one container: {}", name, driver, cs);
                                break;
                            }
                        } else {
                            driver = name;
                        }
                    }
                }
                if(vo.isNoCopy()) {
                    appendOpt(sb, "nocopy");
                }
            }
            Mount.BindOptions bo = mount.getBindOptions();
            if(bo != null) {
                Mount.Propagation propagation = bo.getPropagation();
                if(propagation != null) {
                    appendOpt(sb, propagation.name().toLowerCase());
                }
            }
            cs.getVolumeBinds().add(sb.toString());
        }
        cs.setVolumeDriver(driver);
    }

    private static void appendOpt(StringBuilder sb, String s) {
        int len = sb.length() - 1;
        if(len > 0 && sb.charAt(len) != ',') {
            sb.append(',');
        }
        sb.append(s);
    }

    /**
     * The format of extra hosts on swarmkit is specified in:
     * http://man7.org/linux/man-pages/man5/hosts.5.html
     *    IP_address canonical_hostname [aliases...]
     * @param extraHosts host in 'name:ip' format
     * @return hosts in unix format
     */
    private static List<String> hostsFromSource(List<String> extraHosts) {
        if(extraHosts == null) {
            return null;
        }
        List<String> res = new ArrayList<>(extraHosts.size());
        extraHosts.forEach(src -> {
            String[] hi = StringUtils.split(src, ":");
            if(hi != null) {
                res.add(hi[1] + " " + hi[0]);
            }
        });
        return res;
    }

    /**
     *
     * @param src lines of /etc/hosts file
     * @param dst pairs like 'name:ip'
     */
    private static void hostsToSource(List<String> src, List<String> dst) {
        if(src == null) {
            return;
        }
        src.forEach((hostLine) -> {
            // line in /etc/hosts file
            int sharpPos = hostLine.indexOf("#");
            if(sharpPos == 0) {
                // skip comments
                return;
            }
            String data = hostLine;
            if(sharpPos > 0) {
                data = hostLine.substring(0, sharpPos);
            }
            Iterator<String> i = SP_HOSTS.split(data).iterator();
            if(!i.hasNext()) {
                return;
            }
            String ip = i.next();
            while(i.hasNext()) {
                dst.add(i.next() + ":" + ip);
            }
        });
    }
}
