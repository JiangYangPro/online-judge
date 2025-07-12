package cn.icongyou.service;

import cn.icongyou.common.CodeExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ResultService {
    private static final String PREFIX = "judge:result:";
    private static final Duration EXPIRE = Duration.ofMinutes(30);

    @Autowired
    private RedisTemplate<String, CodeExecutionResult> redisTemplate;

    public void saveResult(CodeExecutionResult result) {
        String key = PREFIX + result.getSubmissionId();
        redisTemplate.opsForValue().set(key, result, EXPIRE);
    }

    public CodeExecutionResult getResult(String submissionId) {
        String key = PREFIX + submissionId;
        return redisTemplate.opsForValue().get(key);
    }
} 