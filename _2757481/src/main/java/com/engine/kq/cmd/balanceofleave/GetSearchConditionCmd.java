package com.engine.kq.cmd.balanceofleave;

import com.api.browser.bean.SearchConditionItem;
import com.api.hrm.bean.SelectOption;
import com.api.hrm.bean.WeaRadioGroup;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.api.hrm.bean.HrmFieldBean;
import com.api.hrm.util.HrmFieldSearchConditionComInfo;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.systeminfo.SystemEnv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 假期余额--高级搜索条件
 */
public class GetSearchConditionCmd extends AbstractCommonCommand<Map<String, Object>> {

    public GetSearchConditionCmd(Map<String, Object> params, User user) {
        this.user = user;
        this.params = params;
    }

    @Override
    public BizLogContext getLogContext() {
        return null;
    }

    @Override
    public Map<String, Object> execute(CommandContext commandContext) {
        Map<String, Object> resultMap = new HashMap<String, Object>();

        List<Map<String, Object>> groupList = new ArrayList<Map<String, Object>>();
        Map<String, Object> groupItem = new HashMap<String, Object>();
        List<Object> itemList = new ArrayList<Object>();
        HrmFieldSearchConditionComInfo hrmFieldSearchConditionComInfo = new HrmFieldSearchConditionComInfo();
        SearchConditionItem searchConditionItem = null;
        HrmFieldBean hrmFieldBean = null;

        groupItem.put("title", SystemEnv.getHtmlLabelName(1889, user.getLanguage()));
        groupItem.put("defaultshow", true);

        String isTiaoxiu = "";
        isTiaoxiu = Util.null2String(params.get("isTiaoxiu"));
        if ("1".equals(isTiaoxiu)){
            //如果为调休界面，显示年月
            itemList.add(getAdvanceDateNew(SystemEnv.getHtmlLabelName(15933, user.getLanguage()).replace("：", ""), user,"searchYear","searchYearSelect"));
        }else {
            itemList.add(getAdvanceDate(SystemEnv.getHtmlLabelName(15933, user.getLanguage()).replace("：", ""), user,"searchYear","searchYearSelect"));
        }

        hrmFieldBean = new HrmFieldBean();
        hrmFieldBean.setFieldname("hrmResourceId");//人员
        hrmFieldBean.setFieldlabel("413");
        hrmFieldBean.setFieldhtmltype("3");
        hrmFieldBean.setType("17");
        hrmFieldBean.setIsFormField(true);
        searchConditionItem = hrmFieldSearchConditionComInfo.getSearchConditionItem(hrmFieldBean, user);
        itemList.add(searchConditionItem);

        groupItem.put("items", itemList);
        groupList.add(groupItem);
        resultMap.put("conditions", groupList);

        return resultMap;
    }

    /**
     * @param label
     * @param user
     * @param domkey
     * @param dateselect
     * @return
     */
    public WeaRadioGroup getAdvanceDateNew(String label, User user, String domkey, String dateselect) {
        List<Object> option = new ArrayList<Object>();
        Map<String, Object> selectLinks = new HashMap<String, Object>();
        List<String> domkeylist = new ArrayList<String>();
        Map<String, Object> map = new HashMap<String, Object>();

        WeaRadioGroup wrg = new WeaRadioGroup();
        wrg.setLabel(label);

        option.add(new SelectOption("5", SystemEnv.getHtmlLabelName(15384, user.getLanguage()), true));
        option.add(new SelectOption("8", SystemEnv.getHtmlLabelName(81716, user.getLanguage())));
        option.add(new SelectOption("6", SystemEnv.getHtmlLabelName(385642,weaver.general.Util.getIntValue(user.getLanguage()))));
        option.add(new SelectOption("9", "年月"));
        wrg.setOptions(option);

        domkeylist.add(dateselect);
        wrg.setDomkey(domkeylist);

        selectLinks.put("conditionType", "DATEPICKER");
        selectLinks.put("viewAttr", 3);
        selectLinks.put("format", "YYYY");
        domkeylist = new ArrayList<String>();
        domkeylist.add(domkey);
        selectLinks.put("domkey", domkeylist);
        map.put("6", selectLinks);

        //保存年月
        selectLinks = new HashMap<>();
        selectLinks.put("conditionType", "DATEPICKER");
        selectLinks.put("viewAttr", 3);
        selectLinks.put("format", "yyyy-MM");
        domkeylist = new ArrayList<String>();
        domkeylist.add("searchYearMonth");
        selectLinks.put("domkey", domkeylist);
        map.put("9", selectLinks);

        wrg.setSelectLinkageDatas(map);

        wrg.setLabelcol(5);
        wrg.setFieldcol(19);
        return wrg;
    }

    /**
     * @param label
     * @param user
     * @param domkey
     * @param dateselect
     * @return
     */
    public WeaRadioGroup getAdvanceDate(String label, User user, String domkey, String dateselect) {
        List<Object> option = new ArrayList<Object>();
        Map<String, Object> selectLinks = new HashMap<String, Object>();
        List<String> domkeylist = new ArrayList<String>();
        Map<String, Object> map = new HashMap<String, Object>();

        WeaRadioGroup wrg = new WeaRadioGroup();
        wrg.setLabel(label);

        option.add(new SelectOption("5", SystemEnv.getHtmlLabelName(15384, user.getLanguage()), true));
        option.add(new SelectOption("8", SystemEnv.getHtmlLabelName(81716, user.getLanguage())));
        option.add(new SelectOption("6", SystemEnv.getHtmlLabelName(385642,weaver.general.Util.getIntValue(user.getLanguage()))));
        wrg.setOptions(option);

        domkeylist.add(dateselect);
        wrg.setDomkey(domkeylist);

        selectLinks.put("conditionType", "DATEPICKER");
        selectLinks.put("viewAttr", 3);
        selectLinks.put("format", "YYYY");
        domkeylist = new ArrayList<String>();
        domkeylist.add(domkey);
        selectLinks.put("domkey", domkeylist);
        map.put("6", selectLinks);

        wrg.setSelectLinkageDatas(map);

        wrg.setLabelcol(5);
        wrg.setFieldcol(19);
        return wrg;
    }
}
