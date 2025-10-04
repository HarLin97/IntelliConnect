/**
 * Copyright © 2023-2030 The ruanrongman Authors
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.rslly.iot.utility.ai.tools;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import top.rslly.iot.models.TimeScheduleEntity;
import top.rslly.iot.services.agent.TimeScheduleServiceImpl;
import top.rslly.iot.utility.QuartzCronDateUtils;
import top.rslly.iot.utility.QuartzManager;
import top.rslly.iot.utility.ai.IcAiException;
import top.rslly.iot.utility.ai.ModelMessage;
import top.rslly.iot.utility.ai.ModelMessageRole;
import top.rslly.iot.utility.ai.llm.LLM;
import top.rslly.iot.utility.ai.llm.LLMFactory;
import top.rslly.iot.utility.ai.prompts.ScheduleToolPrompt;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Data
@Slf4j
public class ScheduleTool implements BaseTool<String> {
  @Autowired
  private ScheduleToolPrompt scheduleToolPrompt;
  @Value("${ai.scheduleTool-llm}")
  private String llmName;
  private String name = "scheduleTool";
  private String description = """
      Used for schedule management and reminder tasks, you can query, cancel, and set schedules.
      Args: User's schedule management needs.(str)
      """;
  @Autowired
  private TimeScheduleServiceImpl timeScheduleService;

  @Override
  public String run(String question) {
    return null;
  }

  @Override
  public String run(String question, Map<String, Object> globalMessage) {
    LLM llm = LLMFactory.getLLM(llmName);
    List<ModelMessage> messages = new ArrayList<>();
    String openid = (String) globalMessage.get("openId");
    String appid = (String) globalMessage.get("microappid");
    ModelMessage systemMessage =
        new ModelMessage(ModelMessageRole.SYSTEM.value(),
            scheduleToolPrompt.getScheduleTool(appid, openid));
    // log.info("systemMessage: " + systemMessage.getContent());
    ModelMessage userMessage = new ModelMessage(ModelMessageRole.USER.value(), question);
    messages.add(systemMessage);
    messages.add(userMessage);
    var obj = llm.jsonChat(question, messages, true).getJSONObject("action");
    String answer = (String) obj.get("answer");
    JSONArray taskParameters = obj.getJSONArray("taskParameters");
    try {
      for (Object taskParameter : taskParameters) {
        process_llm_result((JSONObject) taskParameter, openid, appid);
      }
      return answer;
    } catch (Exception e) {
      // e.printStackTrace();
      log.info("LLM error: " + e.getMessage());
      return "不好意思，提醒任务设置失败了，请检查是否输入时间小于20秒或者没有表达清楚请求。";
    }
  }

  public void process_llm_result(JSONObject jsonObject, String openid, String appid)
      throws IcAiException, ParseException {

    if (jsonObject.get("code").equals("200") || jsonObject.get("code").equals(200)) {
      String groupName = appid + ":" + openid;
      SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      String repeat = jsonObject.getString("repeat");
      String taskName = jsonObject.getString("task_name");
      String taskType = jsonObject.getString("taskType");
      String cron = "";
      if (taskType.equals("set")) {
        String timeStr = jsonObject.getString("time");
        if (repeat.equals("true")) {
          cron = jsonObject.getString("cron");
        } else {
          var time = formatter.parse(timeStr);
          if (time.before(new java.util.Date()))
            throw new IcAiException("time is before now");
          if (time.getTime() - new java.util.Date().getTime() < 20 * 1000)
            throw new IcAiException("time is too near");
          cron = QuartzCronDateUtils.getCron(time);
        }
        // String uuid = UUID.randomUUID().toString();
        if (timeScheduleService.findAllByAppidAndOpenidAndTaskName(appid, openid, taskName)
            .isEmpty()) {
          TimeScheduleEntity timeScheduleEntity = new TimeScheduleEntity();
          timeScheduleEntity.setOpenid(openid);
          timeScheduleEntity.setTaskName(taskName);
          timeScheduleEntity.setCron(cron);
          timeScheduleEntity.setAppid(appid);
          timeScheduleService.insert(timeScheduleEntity);
          QuartzManager.addJob(taskName, groupName, taskName, groupName, RemindJob.class, cron,
              openid,
              appid);
        } else
          throw new IcAiException("task name is duplicate");
      } else if (taskType.equals("cancel")) {
        timeScheduleService.deleteByAppidAndOpenidAndTaskName(appid, openid, taskName);
        QuartzManager.removeJob(taskName, groupName, taskName, groupName);
      }
      log.info("obj:{}", jsonObject);
      log.info("time cron{}", cron);
    } else
      throw new IcAiException("llm response error");
  }
}
