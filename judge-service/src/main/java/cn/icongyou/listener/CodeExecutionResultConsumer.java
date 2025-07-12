package cn.icongyou.listener;

import cn.icongyou.common.CodeExecutionResult;
import cn.icongyou.common.JudgeStatus;
import cn.icongyou.service.ResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @ClassName JudgeResultConsumer
 * @Description 消费判题结果
 * @Author JiangYang
 * @Date 2025/7/9 19:39
 * @Version 1.0
 **/

@Component
public class CodeExecutionResultConsumer {
    private static final Logger logger = LoggerFactory.getLogger(CodeExecutionResultConsumer.class);
    private final ResultService resultService;

    public CodeExecutionResultConsumer(ResultService resultService) {
        this.resultService = resultService;
    }

    @RabbitListener(queues = "result.queue")
    public void receiveResult(CodeExecutionResult result) {
        // 存入Redis
        resultService.saveResult(result);
        // 这里只打印，后续可以写入数据库
        logger.info("✅ 判题结果已返回！");
        logger.info("提交 ID: " + result.getSubmissionId());
        logger.info("状态: " + result.getStatus());
        logger.info("输出: " + result.getStdout());
        logger.info("错误输出: " + result.getStderr());
        logger.info("退出码: " + result.getExitCode());
        logger.info("耗时(ms): " + result.getExecutionTimeMs());
    }
}

