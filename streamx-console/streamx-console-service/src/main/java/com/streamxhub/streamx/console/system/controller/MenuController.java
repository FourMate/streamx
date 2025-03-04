/*
 * Copyright 2019 The StreamX Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.streamxhub.streamx.console.system.controller;

import com.streamxhub.streamx.console.base.domain.RestResponse;
import com.streamxhub.streamx.console.base.domain.router.VueRouter;
import com.streamxhub.streamx.console.core.service.CommonService;
import com.streamxhub.streamx.console.system.entity.Menu;
import com.streamxhub.streamx.console.system.service.MenuService;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author benjobs
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/menu")
public class MenuController {

    @Autowired
    private MenuService menuService;

    @Autowired
    private CommonService commonService;

    @PostMapping("router")
    public RestResponse getUserRouters() {
        ArrayList<VueRouter<Menu>> routers = this.menuService.getUserRouters(commonService.getCurrentUser());
        return RestResponse.success(routers);
    }

    @PostMapping("list")
    @RequiresPermissions("menu:view")
    public RestResponse menuList(Menu menu) {
        Map<String, Object> maps = this.menuService.findMenus(menu);
        return RestResponse.success(maps);
    }

    @PostMapping("post")
    @RequiresPermissions("menu:add")
    public RestResponse addMenu(@Valid Menu menu) {
        this.menuService.createMenu(menu);
        return RestResponse.success();
    }

    @DeleteMapping("delete")
    @RequiresPermissions("menu:delete")
    public RestResponse deleteMenus(@NotBlank(message = "{required}") String menuIds)
        throws Exception {
        String[] ids = menuIds.split(StringPool.COMMA);
        this.menuService.deleteMenus(ids);
        return RestResponse.success();
    }

    @PutMapping("update")
    @RequiresPermissions("menu:update")
    public RestResponse updateMenu(@Valid Menu menu) throws Exception {
        this.menuService.updateMenu(menu);
        return RestResponse.success();
    }

}
