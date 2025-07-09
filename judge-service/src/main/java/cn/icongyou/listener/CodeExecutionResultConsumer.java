package cn.icongyou.listener;

import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.common.JudgeStatus;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @ClassName JudgeResultConsumer
 * @Description TODO
 * @Author JiangYang
 * @Date 2025/7/9 19:39
 * @Version 1.0
 **/

@Component
public class CodeExecutionResultConsumer {

    @RabbitListener(queues = "result.queue")
    public void receiveResult(CodeExecutionResult result) {
        // 这里只打印，后续可以写入数据库
        System.out.println("✅ 判题结果已返回！");
        System.out.println("提交 ID: " + result.getSubmissionId());
        System.out.println("状态: " + result.getStatus());
        System.out.println("输出: " + result.getStdout());
        System.out.println("错误输出: " + result.getStderr());
        System.out.println("退出码: " + result.getExitCode());
        System.out.println("耗时(ms): " + result.getExecutionTimeMs());

        // 示例：如果需要持久化，可以通过 JPA 存入数据库
        if (result.getStatus() == JudgeStatus.COMPILE_ERROR) {
            // 记录 compile 错误日志 或 分析题目难度等
        }
    }
}

