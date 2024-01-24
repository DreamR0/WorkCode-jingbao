package com.api.customization.qc2758032.web;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.api.customization.qc2758032.util.JsonUtil;
import com.api.customization.qc2758032.util.KqReportUtil;
import com.engine.common.util.ParamUtil;
import weaver.general.BaseBean;
import weaver.hrm.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;

@Path("/kq/kqReportCustom")
public class KqReport {

    @GET
    @Path("/getKqReport")
    @Produces(MediaType.APPLICATION_JSON)
    public String getKqReport(@Context HttpServletRequest request, @Context HttpServletResponse response){
        Map<String, Object> apidatas = new HashMap<String, Object>();

        try {
            User user =new User(1);
            JsonUtil jsonUtil = new JsonUtil();

            JSONObject params = jsonUtil.getJson(request);
            new BaseBean().writeLog("==zj==(JSON格式获取数据新)" + JSON.toJSONString(params));
            KqReportUtil kqReportUtil = new KqReportUtil(params, user);

            //获取指定时间范围的考勤数据
            apidatas = kqReportUtil.getKqReport();
        }catch (Exception e){
                apidatas.put("code","0");
                apidatas.put("msg",e);
        }
        return JSONObject.toJSONString(apidatas);
    }

}
