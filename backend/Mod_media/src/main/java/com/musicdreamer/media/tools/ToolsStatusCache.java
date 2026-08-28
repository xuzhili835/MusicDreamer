package com.musicdreamer.media.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具状态查询缓存（ToolsController 5 分钟缓存 / GitHub latest 1 小时缓存）。
 * 独立成组件是为了让安装任务完成后能主动清掉：否则页面 5 分钟内
 * 一直显示旧状态，看起来像"状态是硬编码的"。
 */
@Component
public class ToolsStatusCache {

    private volatile List<Map<String, Object>> status;
    private volatile long statusAt;
    private volatile String latest;
    private volatile long latestAt;

    public List<Map<String, Object>> status() { return status; }
    public long statusAt() { return statusAt; }
    public void putStatus(List<Map<String, Object>> value) {
        this.status = value;
        this.statusAt = System.currentTimeMillis();
    }

    public String latest() { return latest; }
    public long latestAt() { return latestAt; }
    public void putLatest(String value) {
        this.latest = value;
        this.latestAt = System.currentTimeMillis();
    }

    /** 安装/更新完成后调用：下次查询强制重新探测。 */
    public void invalidate() {
        status = null;
        statusAt = 0;
    }
}
