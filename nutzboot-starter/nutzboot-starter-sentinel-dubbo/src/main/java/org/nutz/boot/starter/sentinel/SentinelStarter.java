package org.nutz.boot.starter.sentinel;

import com.alibaba.csp.sentinel.datasource.WritableDataSource;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import org.nutz.boot.annotation.PropDoc;
import org.nutz.boot.starter.ServerFace;
import org.nutz.integration.jedis.RedisService;
import org.nutz.integration.jedis.pubsub.PubSubService;
import org.nutz.ioc.Ioc;
import org.nutz.ioc.impl.PropertiesProxy;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.log.Log;
import org.nutz.log.Logs;

import java.util.List;

@IocBean
public class SentinelStarter implements ServerFace {
    private static final Log log = Logs.get();
    private static final String PRE = "sentinel.";
    @Inject("refer:$ioc")
    protected Ioc ioc;
    @Inject
    protected PropertiesProxy conf;
    private PubSubService pubSubService;
    private RedisService redisService;

    private static final String CONSOLE_SERVER = "csp.sentinel.dashboard.server";
    private static final String SERVER_PORT = "csp.sentinel.api.port";
    private static final String HEARTBEAT_INTERVAL_MS = "csp.sentinel.heartbeat.interval.ms";
    private static final String HEARTBEAT_CLIENT_IP = "csp.sentinel.heartbeat.client.ip";

    @PropDoc(value = "是否启动Sentinel客户端", defaultValue = "false", type = "boolean")
    public static final String PROP_ENABLED = PRE + "enabled";

    @PropDoc(value = "Sentinel客户端名称,不设置则自动获取nutz.application.name", type = "string")
    public static final String PROP_PROJECR_NAME = PRE + "project.name";

    @PropDoc(value = "Sentinel客户端端口", defaultValue = "8721", type = "int")
    public static final String PROP_SERVER_PORT = PRE + SERVER_PORT;

    @PropDoc(value = "Sentinel控制台地址", defaultValue = "localhost:9090", type = "string")
    public static final String PROP_CONSOLE_SERVER = PRE + CONSOLE_SERVER;

    @PropDoc(value = "Sentinel客户端通信间隔毫秒数", defaultValue = "3000", type = "int")
    public static final String PROP_HEARTBEAT_INTERVAL_MS = PRE + HEARTBEAT_INTERVAL_MS;

    @PropDoc(value = "Sentinel客户端IP,不配置则自动获取本地IP", defaultValue = "", type = "string")
    public static final String PROP_HEARTBEAT_CLIENT_IP = PRE + HEARTBEAT_CLIENT_IP;

    @PropDoc(value = "Sentinel规则存储key值", defaultValue = "nutzboot", type = "string")
    public static final String PROP_RULEKEY = PRE + "rulekey";

    @PropDoc(value = "Sentinel规则存储channel值", defaultValue = "sentinel", type = "string")
    public static final String PROP_CHANNEL = PRE + "channel";

    @Override
    public void start() throws Exception {
        if (conf.getBoolean(PROP_SERVER_PORT, false)) {
            redisService = ioc.get(RedisService.class);
            pubSubService = ioc.get(PubSubService.class);
            System.setProperty("java.net.preferIPv4Stack", "true");
            System.setProperty("project.name", conf.get(PROP_PROJECR_NAME, conf.get("nutz.application.name", conf.get("dubbo.application.name", ""))));
            System.setProperty(SERVER_PORT, conf.get(PROP_SERVER_PORT, "8721"));
            System.setProperty(CONSOLE_SERVER, conf.get(PROP_CONSOLE_SERVER, "localhost:9090"));
            System.setProperty(HEARTBEAT_INTERVAL_MS, conf.get(PROP_HEARTBEAT_INTERVAL_MS, "3000"));
            System.setProperty(HEARTBEAT_CLIENT_IP, conf.get(PROP_HEARTBEAT_CLIENT_IP, ""));
            SentinelReadableDataSource<List<FlowRule>> redisDataSource =
                    new SentinelReadableDataSource<List<FlowRule>>(
                            redisService,
                            pubSubService,
                            conf.get(PROP_RULEKEY, "nutzboot"),
                            conf.get(PROP_CHANNEL, "sentinel"),
                            source -> JSON.parseObject(source, new TypeReference<List<FlowRule>>() {
                            }));
            FlowRuleManager.register2Property(redisDataSource.getProperty());
            WritableDataSource<List<FlowRule>> wds = new SentinelWritableDataSource<>(redisService, this::encodeJson, conf.get(PROP_RULEKEY, "nutzboot"));
            // Register to writable data source registry so that rules can be updated to redis
            // when there are rules pushed from the Sentinel Dashboard.
            WritableDataSourceRegistry.registerFlowDataSource(wds);
        }
    }

    private <T> String encodeJson(T t) {
        return JSON.toJSONString(t);
    }

    @Override
    public void stop() throws Exception {

    }

    @Override
    public boolean isRunning() {
        return conf.getBoolean(PROP_SERVER_PORT, false);
    }
}
