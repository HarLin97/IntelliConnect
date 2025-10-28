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
package top.rslly.iot;


import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;
import top.rslly.iot.dao.ProductDeviceRepository;
import top.rslly.iot.dao.DataTimeRepository;
import top.rslly.iot.models.DataEntity;
import top.rslly.iot.models.influxdb.DataTimeEntity;
import top.rslly.iot.param.request.ControlParam;
import top.rslly.iot.utility.DataCleanAuto;
import top.rslly.iot.utility.DataSave;
import top.rslly.iot.utility.QuartzManager;
import top.rslly.iot.utility.SpringBeanUtils;
import top.rslly.iot.utility.ai.Manage;
import top.rslly.iot.utility.ai.chain.Router;
import top.rslly.iot.utility.ai.llm.LLMFactory;
import top.rslly.iot.utility.ai.toolAgent.Agent;
import top.rslly.iot.utility.ai.tools.*;
import top.rslly.iot.utility.ai.voice.Audio2Text;
import top.rslly.iot.utility.ai.voice.TTsMarker;
import top.rslly.iot.utility.influxdb.executor.ExecutorImpl;
import top.rslly.iot.utility.script.ControlScriptFactory;
import top.rslly.iot.utility.script.js.JsScriptInfo;
import top.rslly.iot.utility.script.js.NashornJsInvokeService;

import javax.annotation.Resource;
import javax.script.ScriptException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@Slf4j
class DemoApplicationTests {
  @Autowired
  private DataCleanAuto dataCleanAuto;
  @Autowired
  private ExecutorImpl executor;
  @Autowired(required = false)
  private DataTimeRepository dataTimeRepository;
  @Autowired
  private ControlTool controlTool;
  @Autowired
  private Router router;
  @Autowired
  private Agent agent;
  @Autowired
  private ApplicationContext applicationContext;
  @Autowired
  private NashornJsInvokeService nashornJsInvokeService;
  @Autowired
  private Manage manage;
  @Autowired
  private WxBoundProductTool wxBoundProductTool;
  @Autowired
  private WxProductActiveTool wxProductActiveTool;
  @Resource
  private ProductDeviceRepository productDeviceRepository;
  @Autowired
  ChatTool chatTool;
  @Autowired
  private ScheduleTool scheduleTool;
  @Autowired
  private Audio2Text audio2Text;
  @Autowired
  private TTsMarker tTsMarker;


  @Test
  void testDataSave() {
    Assertions.assertEquals("week", DataSave.week.getStorageType());
    Assertions.assertEquals("never", DataSave.never.getStorageType());
    Assertions.assertEquals("permanent", DataSave.permanent.getStorageType());
  }

  @Test
  void testJsUtil() {
    var name = "test";
    List<String> key = new ArrayList<>();
    List<String> value = new ArrayList<>();
    key.add("brightness");
    value.add("100");
    ControlParam controlParam = new ControlParam(name, "attribute", null, null, 1, key, value);
    Assertions.assertEquals("100", controlParam.getValue().get(0));
  }


  @Test
  void testDataAutoClean() throws IOException {
    dataCleanAuto.task();
  }

  @Test
  public void ping() {
    if (executor.ping()) {
      log.info("连接成功");
    } else {
      log.info("连接失败");
    }
  }

  @Test
  void insert() {
    DataTimeEntity dataTimeEntity = new DataTimeEntity();
    long time = System.currentTimeMillis();
    dataTimeEntity.setTime(time);
    System.out.println(time);
    dataTimeEntity.setDeviceId(1);
    dataTimeEntity.setJsonKey("temp");
    dataTimeEntity.setValue("30");
    dataTimeEntity.setCharacteristic(UUID.randomUUID().toString());
    executor.insert(dataTimeEntity);
  }

  @Test
  public void list() {
    String sql = "SELECT * FROM \"data\" tz('Asia/Shanghai')";
    List<DataTimeEntity> locations = executor.query(DataTimeEntity.class, sql);
    if (locations == null || locations.isEmpty()) {
      log.info("暂无数据");
    }
    System.out.println(locations.get(0).getTime());
  }

  /**
   * 删除数据库
   */
  @Test
  public void delete() {
    // 默认使用配置文件中数据库
    // influxDao.deleteDataBase();
    // 使用指定数据库
    executor.deleteDataBase("data");
  }

  @Test
  public void insert2() {
    DataEntity dataTimeEntity = new DataEntity();
    long time = System.currentTimeMillis();
    dataTimeEntity.setTime(time);
    System.out.println(time);
    dataTimeEntity.setDeviceId(2);
    dataTimeEntity.setJsonKey("temp");
    dataTimeEntity.setValue("90");
    dataTimeEntity.setCharacteristic(UUID.randomUUID().toString());
    executor.insert(dataTimeEntity);
  }

  @Test
  public void time() {
    var res = dataTimeRepository.findAllByTimeBetweenAndDeviceId(0L, System.currentTimeMillis(), 1);
    System.out.println(res.get(0));
  }

  @Test
  public void influxdbMetaData() {
    var res = dataTimeRepository.findAllBySort(1, "color");
    System.out.println(res.toString());
  }

  @Test
  public void Ai() {
    SpringBeanUtils.setApplicationContext(applicationContext);
    // var answer = agent.run("根据新会的天气播放音乐", 1);
    var answer = LLMFactory.getLLM("silicon-Qwen2.5-vl-32B-Instruct").imageToWord("图片里有什么，请简单描述",
        "https://sfile.chatglm.cn/testpath/275ae5b6-5390-51ca-a81a-60332d1a7cac_0.png");
    // var answer= chatTool.run("你好", new ArrayList<>());
    // String answer2 = audio2Text.getText(
    // "https://dashscope.oss-cn-beijing.aliyuncs.com/audios/2channel_16K.wav");

    log.info(answer);
    // log.info(answer2);
  }

  @Test
  public void AiTool() throws InterruptedException {
    SpringBeanUtils.setApplicationContext(applicationContext);
    Map<String, Object> globalMessage = new HashMap<>();
    globalMessage.put("productId", 1);
    var answer = manage.runTool("controlTool", "打开摩托，客厅灯，空调和卧室灯", globalMessage);
    // var answer = agent.run("根据新会的天气控制空调温度", globalMessage);
    log.info(answer);
    // prompt.getControlTool(1);
    // Thread.sleep(100000);
  }

  @Test
  public void TestScript() throws ScriptException, NoSuchMethodException {
    // var body=RuleNodeScriptFactory.generateRuleNodeScript("hello","return \"hello\"");
    // System.out.println(body);
    var jsCode = ControlScriptFactory.generateControlNodeScript("controlFunc",
        """
            var res=control("light1","attribute","","",1,["switch1"],["on"]);
            return res;
            """);
    JsScriptInfo jsScriptInfo = new JsScriptInfo("controlFunc");
    var uuid = UUID.randomUUID();
    // var scriptId=nashornJsInvokeService.doEval(uuid,jsScriptInfo,"function control() { return
    // 'Hello, world!'; }");
    var scriptId = nashornJsInvokeService.doEval(uuid, jsScriptInfo, jsCode);
    // nashornJsInvokeService.compileJs(script);
    try {
      var uuid1 = scriptId.get();
      System.out.println(uuid1);
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    var result = nashornJsInvokeService.doInvokeFunction(uuid, jsScriptInfo, new Object[] {});
    try {
      var a = result.get();
      System.out.println(a);
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
    // nashornJsInvokeService.runJssync("control");
  }

  @Test
  public void testSandBox() throws ScriptException, NoSuchMethodException {
    NashornSandbox sandbox = NashornSandboxes.create();
    String script = "function control() { return 'Hello, world!'; }";
    sandbox.eval(script);
    String result = (String) sandbox.getSandboxedInvocable().invokeFunction("control");
    System.out.println(result);
  }

  @Test
  public void testWxProductBind() {
    var res = wxBoundProductTool.run("绑定lamp设备，密钥是12345");
  }

  @Test
  public void testWxProductActive() {
    var res = wxProductActiveTool.run("设置控制lamp产品");
  }

  @Test
  public void productDeviceCount() {
    var ans = productDeviceRepository.connectStatusCount("connected");
    var ans2 = productDeviceRepository.connectStatusCount("disconnected");
    System.out.println(ans);
    System.out.println(ans2);
  }

  @Test
  public void simpleTest() throws SchedulerException, InterruptedException {
    QuartzManager.addJob("test", "test", "test", "test", RemindJob.class, "0/5 * * * * ?", "test");
    // QuartzManager.startJobs();
    Thread.sleep(100000);
  }

  @Test
  public void ScheduleToolTest() throws InterruptedException {
    SpringBeanUtils.setApplicationContext(applicationContext);
    Map<String, Object> globalMessage = new HashMap<>();
    globalMessage.put("openid", "test");
    globalMessage.put("microappid", "test");
    scheduleTool.run("5分钟后提醒我", globalMessage);
    Thread.sleep(100000);
  }


}
