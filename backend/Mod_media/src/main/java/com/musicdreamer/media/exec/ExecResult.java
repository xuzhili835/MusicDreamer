package com.musicdreamer.media.exec;

import lombok.Builder;
import lombok.Data;

/** 进程执行结果：exitCode、双流输出、是否超时。 */
@Data
@Builder
public class ExecResult {
    private int exitCode;
    private String stdout;
    private String stderr;
    private boolean timedOut;
    private boolean killed;
}
