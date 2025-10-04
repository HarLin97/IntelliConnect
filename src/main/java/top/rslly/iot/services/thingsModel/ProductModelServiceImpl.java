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
package top.rslly.iot.services.thingsModel;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.rslly.iot.dao.*;
import top.rslly.iot.models.ProductDeviceEntity;
import top.rslly.iot.models.ProductEntity;
import top.rslly.iot.models.ProductModelEntity;
import top.rslly.iot.models.WxUserEntity;
import top.rslly.iot.param.prompt.ProductModelDescription;
import top.rslly.iot.param.request.ProductModel;
import top.rslly.iot.utility.JwtTokenUtil;
import top.rslly.iot.utility.result.JsonResult;
import top.rslly.iot.utility.result.ResultCode;
import top.rslly.iot.utility.result.ResultTool;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
public class ProductModelServiceImpl implements ProductModelService {

  @Resource
  private ProductModelRepository productModelRepository;
  @Resource
  private ProductDeviceRepository productDeviceRepository;
  @Resource
  private ProductRepository productRepository;
  @Resource
  private WxProductBindRepository wxProductBindRepository;
  @Resource
  private UserProductBindRepository userProductBindRepository;
  @Resource
  private WxUserRepository wxUserRepository;
  @Resource
  private UserRepository userRepository;

  @Override
  public JsonResult<?> getProductModel(String token) {
    String token_deal = token.replace(JwtTokenUtil.TOKEN_PREFIX, "");
    String role = JwtTokenUtil.getUserRole(token_deal);
    String username = JwtTokenUtil.getUsername(token_deal);
    List<ProductModelEntity> result;
    if (role.equals("ROLE_" + "wx_user")) {
      if (wxUserRepository.findAllByName(username).isEmpty()) {
        return ResultTool.fail(ResultCode.COMMON_FAIL);
      }
      List<WxUserEntity> wxUserEntityList = wxUserRepository.findAllByName(username);
      String appid = wxUserEntityList.get(0).getAppid();
      String openid = wxUserEntityList.get(0).getOpenid();
      result = new ArrayList<>();
      var wxBindProductResponseList =
          wxProductBindRepository.findAllByAppidAndOpenid(appid, openid);
      if (wxBindProductResponseList.isEmpty()) {
        return ResultTool.fail(ResultCode.COMMON_FAIL);
      }
      for (var s : wxBindProductResponseList) {
        List<ProductModelEntity> productModelEntities =
            productModelRepository.findAllByProductId(s.getProductId());
        result.addAll(productModelEntities);
      }
    } else if (!role.equals("[ROLE_admin]")) {
      var userList = userRepository.findAllByUsername(username);
      if (userList.isEmpty()) {
        return ResultTool.fail(ResultCode.COMMON_FAIL);
      }
      int userId = userList.get(0).getId();
      result = new ArrayList<>();
      var userProductBindEntityList = userProductBindRepository.findAllByUserId(userId);
      if (userProductBindEntityList.isEmpty()) {
        return ResultTool.fail(ResultCode.COMMON_FAIL);
      }
      for (var s : userProductBindEntityList) {
        List<ProductModelEntity> productModelEntities =
            productModelRepository.findAllByProductId(s.getProductId());
        result.addAll(productModelEntities);
      }
    } else {
      result = productModelRepository.findAll();
    }
    if (result.isEmpty()) {
      return ResultTool.fail(ResultCode.COMMON_FAIL);
    } else
      return ResultTool.success(result);
  }

  @Override
  public JsonResult<?> getProductModel(int productId) {
    var result = productModelRepository.findAllByProductId(productId);
    if (result.isEmpty()) {
      return ResultTool.fail(ResultCode.COMMON_FAIL);
    } else
      return ResultTool.success(result);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> postProductModel(ProductModel productModel) {
    ProductModelEntity productModelEntity = new ProductModelEntity();
    BeanUtils.copyProperties(productModel, productModelEntity);
    List<ProductEntity> result = productRepository.findAllById(productModel.getProductId());
    List<ProductModelEntity> p1 = productModelRepository
        .findAllByProductIdAndName(productModel.getProductId(), productModel.getName());
    if (result.isEmpty() || !p1.isEmpty())
      return ResultTool.fail(ResultCode.COMMON_FAIL);
    else {
      ProductModelEntity productModelEntity1 = productModelRepository.save(productModelEntity);
      return ResultTool.success(productModelEntity1);
    }
  }

  @Override
  public List<ProductModelDescription> getDescription(int productId) {
    var result = productModelRepository.findAllByProductId(productId);
    List<ProductModelDescription> productModelDescriptionList = new LinkedList<>();
    if (!result.isEmpty()) {
      for (var s : result) {
        ProductModelDescription productModelDescription = new ProductModelDescription();
        productModelDescription.setName(s.getName());
        productModelDescription.setDescription(s.getDescription());
        productModelDescriptionList.add(productModelDescription);
      }
    }
    return productModelDescriptionList;
  }

  @Override
  public List<ProductModelEntity> findAllById(int id) {
    return productModelRepository.findAllById(id);
  }

  @Override
  public List<ProductModelEntity> findAllByProductId(int productId) {
    return productModelRepository.findAllByProductId(productId);
  }

  @Override
  public List<ProductModelEntity> findAllByProductIdAndName(int productId, String name) {
    return productModelRepository.findAllByProductIdAndName(productId, name);
  }


  @Override
  @Transactional(rollbackFor = Exception.class)
  public JsonResult<?> deleteProductModel(int id) {
    List<ProductDeviceEntity> productDeviceEntityList =
        productDeviceRepository.findAllByModelId(id);
    if (productDeviceEntityList.isEmpty()) {
      List<ProductModelEntity> result = productModelRepository.deleteById(id);
      if (result.isEmpty())
        return ResultTool.fail(ResultCode.PARAM_NOT_VALID);
      else {
        return ResultTool.success(result);
      }
    } else {
      return ResultTool.fail(ResultCode.HAS_DEPENDENCIES);
    }

  }
}
