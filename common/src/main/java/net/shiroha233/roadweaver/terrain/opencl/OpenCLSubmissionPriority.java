package net.shiroha233.roadweaver.terrain.opencl;

/**
 * 共享设备会话的提交优先级。
 */
enum OpenCLSubmissionPriority {
    ACCURATE,
    COARSE,
    MAINTENANCE
}
