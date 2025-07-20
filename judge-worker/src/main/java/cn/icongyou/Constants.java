package cn.icongyou;

import org.springframework.beans.factory.annotation.Value;

/**
 * @ClassName cn.icongyou.Constants
 * @Description 全局常量
 * @Author JiangYang
 * @Date 2025/7/13 10:06
 * @Version 1.0
 **/
public class Constants {
    public static final String JUDGE_QUEUE = "judge.queue";
    public static final String RESULT_QUEUE = "result.queue";

    public static final String BASE_IMAGE = "openjdk:8-jdk-alpine";

    public static final String CONTAINER_PREFIX = "judge-pool-";

    /*
        线程池参数配置
    */
    public static final int CORE_SIZE = 10;
    public static final int MAX_SIZE = 20;
    public static final int KEEP_ALIVE_SECONDS = 60;
    public static final int QUEUE_CAPACITY = 100;

}
