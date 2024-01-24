package com.api.customization.qc2757481.web;

import com.alibaba.fastjson.JSON;
import com.api.customization.qc2757481.KqCustomUtil;
import com.engine.common.util.ParamUtil;
import weaver.hrm.HrmUserVarify;
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
public class KqCustomWeb {


    @GET
    @Path("/isTiaoxiu")
    @Produces(MediaType.TEXT_PLAIN)
    public String isTiaoxiu(@Context HttpServletRequest request, @Context HttpServletResponse response){
        Map<String, Object> apidatas = new HashMap<String, Object>();
        KqCustomUtil kqCustomUtil = new KqCustomUtil();
        try {
            User user = HrmUserVarify.getUser(request, response);
            apidatas = kqCustomUtil.isTiaoXiu(ParamUtil.request2Map(request), user);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return JSON.toJSONString(apidatas);
    }

    @POST
    @Path("/saveFailurePaid")
    @Produces(MediaType.TEXT_PLAIN)
    public String saveFailurePaid(@Context HttpServletRequest request, @Context HttpServletResponse response){
        Map<String, Object> apidatas = new HashMap<String, Object>();
        KqCustomUtil kqCustomUtil = new KqCustomUtil();
        try {
            User user = HrmUserVarify.getUser(request, response);
            apidatas = kqCustomUtil.saveFailurePaid(ParamUtil.request2Map(request), user);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return JSON.toJSONString(apidatas);
    }
}
