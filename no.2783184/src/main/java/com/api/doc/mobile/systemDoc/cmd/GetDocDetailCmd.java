package com.api.doc.mobile.systemDoc.cmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSON;
import com.api.doc.detail.util.DocSecretLevelUtil;
import com.engine.doc.util.CheckPermission;
import com.engine.doc.util.DocEncryptUtil;
import org.apache.commons.lang.StringEscapeUtils;
import weaver.conn.RecordSet;
import weaver.docs.category.SecCategoryComInfo;
import weaver.docs.docs.DocImageManager;
import weaver.docs.docs.DocManager;
import weaver.docs.docs.reply.DocReplyManager;
import weaver.docs.docs.reply.PraiseInfo;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.hrm.HrmUserVarify;
import weaver.hrm.User;
import weaver.hrm.UserManager;
import weaver.hrm.resource.ResourceComInfo;
import weaver.splitepage.operate.SpopForDoc;
import weaver.splitepage.transform.SptmForDoc;

import com.api.doc.detail.service.DocDetailService;
import com.api.doc.detail.service.DocViewPermission;
import com.api.doc.detail.util.ImageConvertUtil;
import com.api.doc.edit.util.EditConfigUtil;
import com.api.doc.mobile.systemDoc.util.DocDetailUtil;
import com.api.doc.mobile.systemDoc.util.SystemDocUtil;
import com.api.doc.search.service.DocLogService;
import com.api.networkdisk.util.DocIconUtil;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.doc.util.WaterMarkUtil;
import com.engine.msgcenter.util.ValveConfigManager;
import weaver.systeminfo.SystemEnv;

/**
 * author：tongh
 * date：2018/9/12 14:08
 * detail： 获取文档详情
 * copy 自 mobile/plugin/networkdisk/systemDoc.jsp
 * 文档状态  0:草稿
 * 1:生效/正常(不需要审批，归档重新打开)
 * 2: 生效/正常(审批后,发布后)
 * 3:审批
 * 4:退回(草稿)
 * 5:归档
 * 6:待发布
 * 7:失效
 * 8:作废
 * 9:流程草稿
 */
public class GetDocDetailCmd extends AbstractCommonCommand<Map<String, Object>> {

    public GetDocDetailCmd(Map<String,Object> params, User user) {
        this.user = user;
        this.params = params;
    }

    @Override
    public BizLogContext getLogContext() {
        return null;
    }

    @Override
    public Map<String, Object> execute(CommandContext commandContext) {
        Map<String,Object> apidatas = new HashMap<String,Object>();
        boolean docisLock = false;//是否锁文档
        try { //获取文档详情

            HttpServletRequest request = (HttpServletRequest) params.get("request");
            HttpServletResponse response = (HttpServletResponse) params.get("response");
            int docid = Util.getIntValue((String) params.get("docid"),0);

            String from = Util.null2s((String) params.get("from"),"");
            Map<String,String> onlineParams = new HashMap<>();
            onlineParams.put("checkPic","1");
            //文档处理基本类
            DocManager docManager = new DocManager();
            //文档操作权限类
            SpopForDoc spopForDoc = new SpopForDoc();
            //文档信息日志类
            //DocDetailLog docDetailLog = new DocDetailLog();
            //人力资源基本信息缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //文档回复管理类
            DocReplyManager docReply = new DocReplyManager();
            //文档图片管理类
            DocImageManager docImageManager = new DocImageManager();
            if(docid == 0){
                apidatas.put("api_status",false);
            }else{
                RecordSet rs = new RecordSet();
                RecordSet rsversion = new RecordSet();
                String sql = "select DocSubject from Docdetail where id="+docid;
                rs.executeQuery(sql);
                apidatas.put("isHaveDoc","1");
                if(!rs.next()){
                    apidatas.put("isHaveDoc","0");
                    apidatas.put("api_status",true);
                }else{

                    //0:查看
                    boolean canReader = false;
                    //1:编辑
                    boolean canEdit = false;
                    //2:删除
                    boolean canDel = false;
                    //3:共享
                    boolean canShare = false ;
                    //4:是否单附件打开
                    boolean canOpenSingleAttach = false;
                    //5:下载
                    boolean candownload = false;

                    Map<String,Object> mainFileInfo = new HashMap<>();
                    String mainFileid = "";
                    String mainext = "";
                    boolean mainFileOnline = false;
                    BaseBean bb = new BaseBean();
                    //判断pdf是否走永中转换,默认不走
                    String isPdfConvert = Util.null2String(bb.getPropValue("doc_custom_for_weaver","pdfConvert"),"0");
                    if(isPdfConvert.equals("")){
                        isPdfConvert = "0";
                    }

                    int userid=user.getUID();
                    //获取用户类型 1 内部用户 2 外部用户
                    String logintype = user.getLogintype();
                    String userType = ""+user.getType();
                    String userdepartment = ""+user.getUserDepartment();
                    String usersubcomany = ""+user.getUserSubCompany1();
                    //获取安全级别
                    String userSeclevel = user.getSeclevel();
                    String userSeclevelCheck = userSeclevel;
                    //如果是外部用户
                    if("2".equals(logintype)){
                        userdepartment="0";
                        usersubcomany="0";
                        userSeclevel="0";
                    }

                    //重置各参数的值
                    docManager.resetParameter();
                    //设置文档id
                    docManager.setId(docid);
                    //通过文档ID得到相应的文档信息
                    docManager.getDocInfoById();
                    //返回文档子目录
                    int seccategory=docManager.getSeccategory();

                    SecCategoryComInfo scci = new SecCategoryComInfo();
                    String  seccategoryName = scci.getAllParentName("" + docManager.getSeccategory(),true);//文档所在目录
                    //返回文档内容
                    String doccontent=docManager.getDoccontent();
                    //返回 文档发布类型
                    String docpublishtype=docManager.getDocpublishtype();
                    //文档状态
                    String docstatus=docManager.getDocstatus();
                    int accessorycount = docManager.getAccessorycount();
                    //
                    // int ishistory = docManager.getIsHistory();

                    //子目录信息  执行存储过程
                    // rs.executeProc("Doc_SecCategory_SelectByID",seccategory+"");
                    // rs.next();
                    // String readerCanViewHistoryEdition=Util.null2String(rs.getString("readerCanViewHistoryEdition"));

                    // String userInfo=logintype+"_"+userid+"_"+userSeclevelCheck+"_"+userType+"_"+userdepartment+"_"+usersubcomany;
                    //返回对文档的各种操作权限，false为无权限，true为有权限
                    // List PdocList = spopForDoc.getDocOpratePopedom("" + docid,userInfo);

                    //文档类型，如果是1为html文档，如果
                    int doctype = docManager.getDocType();
                    DocDetailService detailService = new DocDetailService();
                    doccontent = detailService.getDocContent(docid,user);
                    //判断当前文档是否开启单附件打开
                    if(doctype == 1){
                        canOpenSingleAttach = SystemDocUtil.canOpenSingleAttach(docid,doccontent);
                    }else{
                        canOpenSingleAttach = true;
                    }
                    String officeTypesql = "select a.imagefileid,a.imagefilename,a.docfiletype,a.versionId,b.filesize from DocImageFile a,Imagefile b " +
                            " where a.imagefileid=b.imagefileid and a.docid=? and (a.isextfile <> '1' or a.isextfile is null) order by a.versionId desc ";
                    RecordSet officeTypeRs = new RecordSet();
                    officeTypeRs.executeQuery(officeTypesql,docid);
                    String officeImagefileid = "";
                    if(officeTypeRs.next()){
                        officeImagefileid = Util.null2String(officeTypeRs.getString("imagefileid"));
                    }

                   /* if (((String)PdocList.get(0)).equals("true")) canReader = true ;//查看
                    if (((String)PdocList.get(1)).equals("true")) canEdit = true ;//编辑
                    if (((String)PdocList.get(2)).equals("true")) canDel = true ;//删除
                    if (((String)PdocList.get(3)).equals("true")) canShare = true ;//共享
                    if (((String)PdocList.get(5)).equals("true")) candownload = true;//下载


                    //7:失效 8:作废
                    if(canReader && ((!docstatus.equals("7")&&!docstatus.equals("8"))
                            ||(docstatus.equals("7")&&ishistory==1&&readerCanViewHistoryEdition.equals("1")))){
                        canReader = true;
                    }else{
                        canReader = false;
                    }
                    //是否可以查看历史版本
                    //具有编辑权限的用户，始终可见文档的历史版本；
                    //可以设置具有只读权限的操作人是否可见历史版本；
                    if(ishistory==1) {
                        if(readerCanViewHistoryEdition.equals("1")){
                            if(canReader && !canEdit) canReader = true;
                        } else {
                            if(canReader && !canEdit) canReader = false;
                        }
                    }

                    //编辑权限操作者可查看文档状态为：“审批”、“归档”、“待发布”或历史文档
                    if(canEdit && ((docstatus.equals("3") || docstatus.equals("5") || docstatus.equals("6") || docstatus.equals("7")) || ishistory==1)) {
                        canReader = true;
                    }
                    */
                    DocViewPermission dvp = new DocViewPermission();
                    if(dvp.hasRightForSecret(user,docid)) {   //密级等级判断
                        Map<String, Boolean> levelMap = dvp.getShareLevel(docid, user, false);
                        canReader = levelMap.get(DocViewPermission.READ);
                        canEdit = levelMap.get(DocViewPermission.EDIT);
                        canDel = levelMap.get(DocViewPermission.DELETE);
                        canShare = levelMap.get(DocViewPermission.SHARE);
                        candownload = levelMap.get(DocViewPermission.DOWNLOAD);
                        if (!canReader) {
                            levelMap.put(DocViewPermission.READ, dvp.hasRightFromOtherMould(docid, user, request));
                            canReader = levelMap.get(DocViewPermission.READ);
                        }
                    }



                    if(canReader){
                        apidatas.put("api_status",true);
                        com.api.doc.detail.util.DocDetailUtil.getDocFirstfile(docid+"");
                        if(docpublishtype.equals("2")){
                            int tmppos = doccontent.indexOf("!@#$%^&*");
                            if(tmppos!=-1) doccontent = doccontent.substring(tmppos+8,doccontent.length());
                        }
                        doccontent=doccontent.replaceAll("<meta.*?/>","");
                        Map<String,Object> docInfo = new HashMap<String,Object>();


                        Map<String,String> encryptInfo = new HashMap<>();
                        encryptInfo = DocEncryptUtil.EncryptInfo(docid);
                        docInfo.put("isencryptable",Util.null2s(encryptInfo.get(DocEncryptUtil.ISENCRYPTSHARE),"0"));
                        docInfo.put("isEnableSecondAuth",Util.null2s(encryptInfo.get(DocEncryptUtil.ISENABLESECONDAUTH),"0"));
                        docInfo.put("isonlyencryptshare",Util.null2s(encryptInfo.get(DocEncryptUtil.ENCRYPTRANGE),"0"));

                        mainFileid = com.api.doc.detail.util.DocDetailUtil.getMainImagefile(docid+"");
                        //getDoccreaterid 返回文档创建者id
                        if( (userid != docManager.getDoccreaterid() || !docManager.getUsertype().equals(logintype)) && "".equals(from) ) {
                            /*char flag=Util.getSeparator() ;
                            rs.executeProc("docReadTag_AddByUser",""+docid+flag+userid+flag+logintype);
                            docDetailLog.resetParameter();
                            docDetailLog.setDocId(docid);
                            docDetailLog.setDocSubject(docManager.getDocsubject());
                            docDetailLog.setOperateType("0");
                            docDetailLog.setOperateUserid(user.getUID());
                            docDetailLog.setUsertype(user.getLogintype());
                            docDetailLog.setClientAddress(request.getRemoteAddr());
                            docDetailLog.setDocCreater(docManager.getDoccreaterid());
                            docDetailLog.setDocLogInfo();*/
                            DocLogService dls = new DocLogService();
                            dls.addReadLog(docid,user,request.getRemoteAddr());
                        }
                        doccontent = Util.replace(doccontent, "&amp;", "&", 0);
                        doccontent = doccontent.replace("<meta content=\"text/html; charset=utf-8\" http-equiv=\"Content-Type\"/>","");
                        docInfo.put("canDelete",canDel);  //是否可删除
                        docInfo.put("canShare",canShare); //是否可分享
                        String sqlshare = "select allowoutshare,hideshare from docseccategory where id = (select seccategory from docdetail where id = ?)";
                        RecordSet rsshare = new RecordSet();
                        rsshare.executeQuery(sqlshare,docid);
                        boolean canoutshare = false;
                        String hideshare = "0";
                        if(rsshare.next()){
                            if("1".equals(rsshare.getString("allowoutshare"))){
                                canoutshare = true;
                            }
                            hideshare = Util.null2String(rsshare.getString("hideshare"));
                        }
                        docInfo.put("docTitle",docManager.getDocsubject()); // 文档标题
                        docInfo.put("canoutshare",canoutshare); // 文档标题
                        //
                        String docTitleLabel = DocDetailUtil.getMobileDocSubjectLabel(seccategory,user);
                        docInfo.put("docTitleLabel",docTitleLabel);

                        //docInfo.put("doccontent",doccontent);  //文档内容
                        docInfo.put("owner",resourceComInfo.getLastname(docManager.getOwnerid()+""));  //所有者
                        docInfo.put("docCreater",resourceComInfo.getLastname(docManager.getDoccreaterid()+""));  //文档创建者
                        docInfo.put("docCreateTime",docManager.getDoccreatedate()+" "+docManager.getDoccreatetime());  //文档创建时间
                        //docInfo.put("doccontent",doccontent);  //文档内容
                        docInfo.put("seccategory",seccategoryName);
                        docInfo.put("showsharebtn","1".equals(hideshare)?"1":"0");


                        boolean isContentEmpty = DocDetailService.ifContentEmpty(doccontent);
                        docInfo.put("doccontent",replaceip(doccontent));  //文档内容
                        docInfo.put("ownerHeaderUrl",resourceComInfo.getMessagerUrls(docManager.getOwnerid()+""));  //所有者头像地址
                        docInfo.put("ownerid",docManager.getOwnerid() + ""); //所有者id
                        docInfo.put("updateUser",resourceComInfo.getLastname(docManager.getDoclastmoduserid() + "")); //最后更新人
                        docInfo.put("updateTime",docManager.getDoclastmoddate() + " " + docManager.getDoclastmodtime());  //最后更新时间
                        docInfo.put("readCount","0");  //阅读数量
                        String prohibitDownloadSwatch = ValveConfigManager.getTypeValve("prohibitDownload", "prohibitDownloadSwatch", "0");
                        if("1".equals(prohibitDownloadSwatch)){
                            candownload = false;
                        }
                        docInfo.put("candownload",candownload);
                        String doc_acc_isalert_singleatt = Util.null2String(bb.getPropValue("doc_mobile_detail_prop","doc_acc_isalert_singleatt"),"0");
                        docInfo.put("doc_acc_isalert_singleatt",doc_acc_isalert_singleatt);
                        String doc_pdf_download = Util.null2s(bb.getPropValue("doc_pdf_download_bl","doc_pdf_download"),"0");
                        docInfo.put("doc_pdf_download",doc_pdf_download);
                        docInfo.put("doctype",doctype);
                        docInfo.put("officeImagefileid",officeImagefileid);


                        String isshowLog = "0";
                        UserManager um = new UserManager();
                        User userTmp = um.getUserByUserIdAndLoginType(user.getUID(), user.getLogintype());
                        DocViewPermission docViewPermission = new DocViewPermission();
                        Map<String,Boolean> rightMap = docViewPermission.getShareLevel(docid, user, false);
                        Boolean canEditDoc = rightMap.get(DocViewPermission.EDIT);
                        new BaseBean().writeLog("==zj==(canEditDoc)" + canEditDoc);
                        RecordSet rslog = new RecordSet();
                        rslog.executeQuery("select c.logviewtype,c.editionIsOpen,c.markable,c.relationable,c.replyable,d.docstatus " +
                                "from DocSecCategory c,DocDetail d where c.id=d.seccategory and d.id=" + docid);
                        rslog.next();
                        if (!"2".equals(user.getLogintype()) &&
                                (!"1".equals(rslog.getString("logviewtype")) || (canEditDoc || HrmUserVarify.checkUserRight("FileLogView:View", userTmp)))){
                            isshowLog = "1";
                        }
                        docInfo.put("isshowLog",isshowLog);
                        //获取当前文档的状态
                        SptmForDoc sptmForDoc = new SptmForDoc();
                        //当前文档id
                        String docid1 = Util.null2String((String) params.get("docid"));
                        String docstatusname = sptmForDoc.getDocStatus3(docid1, ""+user.getLanguage()+"+"+docstatus+"+"+seccategory);
                        docInfo.put("docstatus", docstatusname);
                        docInfo.put("docstatusnum", docstatus);


                        rs.executeSql("select count(1) num from DocDetailLog where docid="+docid+" and operatetype = 0");
                        if(rs.next()){
                            docInfo.put("readCount",rs.getString("num"));
                        }
                        docInfo.put("canReply",false);  //是否允许回复
                        rs.executeSql("select replyable,defaultlockeddoc from DocSecCategory where id=" + seccategory);
                        if(rs.next()){
                            docInfo.put("canReply","1".equals(rs.getString("replyable")));
                            docisLock = "1".equals(rs.getString("defaultlockeddoc"));//是否锁定
                        }
                        if(canEdit){
                            docisLock = false;
                        }
                        rs.executeSql("select count(1) num from DOC_REPLY where docid='" + docid + "'");
                        docInfo.put("replyCount","0"); //回复数
                        if(rs.next()){
                            docInfo.put("replyCount",rs.getString("num"));
                        }

                        PraiseInfo praiseInfo = docReply.getPraiseInfoByDocid(docid + "",user.getUID());
                        docInfo.put("praiseCount","0"); //数量
                        docInfo.put("isPraise",false); //是否过
                        if(praiseInfo.getUsers() != null){
                            docInfo.put("praiseCount",praiseInfo.getUsers().size() + "");
                            docInfo.put("isPraise",praiseInfo.getIsPraise() == 1 ? true : false);
                        }
                        docInfo.put("isCollute",false); //是否收藏过
                        rs.executeSql("select id from SysFavourite where favouriteObjId=" + docid + " and favouritetype=1 and Resourceid="+user.getUID());
                        if(rs.next()){
                            docInfo.put("isCollute",true);
                        }
                        ImageConvertUtil icu = new ImageConvertUtil();
                        //String icon = resourceComInfo.getMessagerUrls(docManager.getOwnerid() + "");
                        String  icon = User.getUserIcon(docManager.getOwnerid() + "");
                        String sex = resourceComInfo.getSexs(docManager.getOwnerid() + "");
                        String errorIcon = icon;
                        if("1".equals(sex)){
                            // icon = weaver.hrm.User.getUserIcon(docManager.getOwnerid() + "");
                            //errorIcon = "/messager/images/icon_w_wev8.jpg";
                        }else{
                            //  icon = "/messager/images/icon_m_wev8.jpg";
                            //errorIcon = "/messager/images/icon_m_wev8.jpg";
                        }
                        docInfo.put("icon",icon);//所有者头像
                        docInfo.put("errorIcon",errorIcon); // 所有者头像加载错误时加载的图片
                        //rs.executeSql("select f.imagefileid,f.imagefilename,f.filesize,df.docid from DocImageFile df,ImageFile f where df.imagefileid=f.imagefileid and df.docid=" + docid);
                        //附件上传信息
                        Map<String,String> upload = EditConfigUtil.getFileUpload(user.getLanguage(),null,docid,seccategory);
                        docInfo.put("maxUploadSize",upload.get("maxUploadSize"));
                        docInfo.put("limitType",upload.get("limitType"));
                        int docImageId = 0;
                        //DocImageManager docImageManager = new DocImageManager();
                        docImageManager.resetParameter();
                        docImageManager.setDocid(docid);
                        docImageManager.selectDocImageInfo();
                        List<Map<String,Object>> docAttrs = new ArrayList<Map<String,Object>>();
                        docInfo.put("docAttrs",docAttrs); //附件列表

                        DocDetailService docService = new DocDetailService();
                        Map<String,Object> docParams = new HashMap<String,Object>();
                        docParams = docService.getDocParamInfo(docid,user,true);

                        docInfo.put("docParams",docParams.get("data"));//文档基本属性

                        weaver.docs.docs.util.DesUtils des = null;
                        try{
                            des = new weaver.docs.docs.util.DesUtils();

                        }catch(Exception e){
                        }

                        rs.executeQuery("select * from DocImageFile where docid="+docid+" and (isextfile <> '1' or isextfile is null) and docfiletype <> '1' order by versionId desc");
                        if(rs.next()) {
                            Map<String,Object> docAttr = new HashMap<String,Object>();
                            docAttrs.add(docAttr);
                            String imfid=rs.getString("imagefileid");
                            String fname=Util.null2String(rs.getString("imagefilename"));
                            long fSize = docImageManager.getImageFileSize(Util.getIntValue(imfid));
                            String ficon = "general_icon.png";


                            String docImagefileSizeStr = "";
                            if(fSize / (1024 * 1024) > 0) {
                                docImagefileSizeStr = (fSize / 1024 / 1024) + "M";
                            } else if(fSize / 1024 > 0) {
                                docImagefileSizeStr = (fSize / 1024) + "K";
                            } else {
                                docImagefileSizeStr = fSize + "B";
                            }
                            String extName = fname.contains(".")? fname.substring(fname.lastIndexOf(".") + 1) : "";


                            Map<String, String> iconMap = DocIconUtil.getDocIconDetail(extName);
                            boolean tooLarge = icu.isTooLarge(extName,fSize + "");
                            if(tooLarge && "pdf".equals(extName.toLowerCase())){
                                tooLarge = false;
                            }
                            onlineParams.put("docid",docid+"");
                            boolean readOnLine = ImageConvertUtil.readOnlineForMobile(extName,onlineParams);//是否支持在线查看
                            if(tooLarge){
                                readOnLine = false;
                            }
                            if(fname.endsWith(".html")||fname.endsWith(".htm")){
                                RecordSet rs_tmp = new RecordSet();
                                String worflowhtmlSql = " select comefrom from imagefile where imagefileid = ?";
                                rs_tmp.executeQuery(worflowhtmlSql,""+imfid);
                                if(rs_tmp.next()){
                                    String htmlcomefrom = Util.null2String(rs_tmp.getString("comefrom"));
                                    if("WorkflowToDoc".equals(htmlcomefrom)){
                                        readOnLine = true;
                                    }
                                }
                            }
                            /*String idsql = "(select max(id) maxid from DocImageFile where imagefileid=" + imfid + ")";
                            String sqlversion="select * from DocImageFile where id="+idsql+" order by versionid desc";
                            rsversion.executeQuery(sqlversion);*/
                            //int versionnum=rsversion.getCounts();
                            docAttr.put("tooLarge",tooLarge ? "1" : "0");
                            docAttr.put("imagefileid",imfid);
                            docAttr.put("officeDoc","1");
                            // docAttr.put("versionnum",versionnum);
                            docAttr.put("docid",docid+"");
                            docAttr.put("filename",StringEscapeUtils.unescapeHtml(fname));
                            docAttr.put("ficon", iconMap);
                            docAttr.put("fileSizeStr",docImagefileSizeStr);
                            docAttr.put("readOnLine",readOnLine ? "1" : "0");
                            docAttr.putAll(DocSecretLevelUtil.takeSecretInfobyID(imfid,user));
                            if (officeImagefileid.equals(imfid)){
                                mainFileOnline = readOnLine;
                                mainext  =extName;
                            }
                            if(SystemDocUtil.canEditForMobile(extName,user)){
                                //==zj==移动端正文编辑控制
                                boolean isOpenWps = false;	//设置开关状态
                                User user1 = HrmUserVarify.getUser (request,response);
                                try{
                                    //查看开关状态
                                    RecordSet wps = new RecordSet();
                                    wps.executeQuery("select isopen from wps_config where type='wps'");
                                    if (wps.next()){
                                        int isOpen = wps.getInt("isopen");
                                        //如果开关是开启状态
                                        if (isOpen == 1){
                                            int subcompanyName = -1;		//分部id
                                            subcompanyName =user.getUserSubCompany1();//获取该用户的分部id

                                            RecordSet sub = new RecordSet();
                                            /*String Docsql = "select subcompanyname from uf_wps where subcompanyname in ("+subcompanyName+")";*/
                                            String Docsql = "select * from uf_wps where instr(',' || subcompanyname || ',', ',' || " + subcompanyName+ " || ',')>0";
                                            new BaseBean().writeLog("(canEditDoc是否显示编辑按钮)" + Docsql);
                                            sub.executeQuery(Docsql);
                                            if (sub.next()){
                                                isOpenWps = true;
                                            }
                                        }
                                    }
                                }catch (Exception e){
                                    new BaseBean().writeLog("canEditDoc报错:" + e);
                                }
                                new BaseBean().writeLog("canEditDoc权限:"+user.getUID()+" | " + canEditDoc + " | " + isOpenWps);
                                docAttr.put("canEditDoc",isOpenWps);

                            }

                            String ddcode = SystemDocUtil.takeddcode(user,imfid,null);
                            docAttr.put("ddcode","");

                            request.getSession().setAttribute("docAttr_" + user.getUID() + "_" + docAttr.get("docid") + "_" + docAttr.get("imagefileid"),"1");
                        }
                        boolean ismainFile = false;
                        while (docImageManager.next()) {
                            int temdiid = docImageManager.getId();
                            if (temdiid == docImageId) {
                                continue;
                            }
                            docImageId = temdiid;
                            Map<String,Object> docAttr = new HashMap<String,Object>();
                            docAttrs.add(docAttr);
                            String filename = Util.null2String(docImageManager.getImagefilename());
                            int filesize = docImageManager.getImageFileSize(Util.getIntValue(docImageManager.getImagefileid()));
                            String extName = filename.contains(".")? filename.substring(filename.lastIndexOf(".") + 1) : "";
                            Map<String, String> iconMap = DocIconUtil.getDocIconDetail(extName);
                            String ficon = "general_icon.png";
                            boolean tooLarge = icu.isTooLarge(extName,filesize + "");
                            if(tooLarge && "pdf".equals(extName.toLowerCase())){
                                tooLarge = false;
                            }
                            onlineParams.put("docid",docid+"");
                            boolean readOnLine = ImageConvertUtil.readOnlineForMobile(extName,onlineParams);//是否支持在线查看
                            if(tooLarge){
                                readOnLine = false;
                            }
                            if(filename.endsWith(".html")||filename.endsWith(".htm")){
                                RecordSet rs_tmp = new RecordSet();
                                String worflowhtmlSql = " select comefrom from imagefile where imagefileid = ?";
                                rs_tmp.executeQuery(worflowhtmlSql,""+docImageManager.getImagefileid());
                                if(rs_tmp.next()){
                                    String htmlcomefrom = Util.null2String(rs_tmp.getString("comefrom"));
                                    if("WorkflowToDoc".equals(htmlcomefrom)){
                                        readOnLine = true;
                                    }
                                }
                            }
                            String fileSizeStr = "";
                            if(filesize / (1024 * 1024) > 0) {
                                fileSizeStr = (filesize / 1024 / 1024) + "M";
                            } else if(filesize / 1024 > 0) {
                                fileSizeStr = (filesize / 1024) + "K";
                            } else {
                                fileSizeStr = filesize + "B";
                            }
                            String fileidvalue = Util.null2String(docImageManager.getImagefileid());
                            String idsql = "(select max(id) maxid from DocImageFile where imagefileid=" + fileidvalue + ")";
                            String sqlversion="select * from DocImageFile where id="+idsql+" order by versionid desc";
                            rsversion.executeQuery(sqlversion);
                            int versionnum=rsversion.getCounts();
                            docAttr.put("tooLarge",tooLarge ? "1" : "0");
                            docAttr.put("imagefileid",fileidvalue);
                            docAttr.put("versionnum",versionnum);
                            docAttr.put("docid",docid+"");
                            docAttr.put("filename",StringEscapeUtils.unescapeHtml(filename));
                            docAttr.put("ficon",iconMap);
                            docAttr.put("fileSizeStr",fileSizeStr);
                            docAttr.put("readOnLine",readOnLine ? "1" : "0");
                            docAttr.putAll(DocSecretLevelUtil.takeSecretInfobyID(fileidvalue,user));
                            ismainFile = mainFileid.equals(fileidvalue);
                            if(accessorycount == 1 && readOnLine){
                                ismainFile = true;
                            }
                            if (ismainFile && officeImagefileid.isEmpty()){
                                mainFileOnline = ismainFile && readOnLine;
                                mainext  =extName;
                                mainFileid = fileidvalue;
                            }
                            docAttr.put("mainFile",ismainFile?SystemEnv.getHtmlLabelName(514544,user.getLanguage()):"");
                            new BaseBean().writeLog("是否可修改:" +user.getUID() + " | "+ canEditDoc + " | " + filename);
                            if(canEditDoc && SystemDocUtil.canEditForMobile(extName,user)){
                                boolean isOpenWps = false;	//设置开关状态
                                try{
                                    /*//查看开关状态
                                    RecordSet wps = new RecordSet();
                                    wps.executeQuery("select isopen from wps_config where type='wps'");
                                    if (wps.next()){
                                        int isOpen = wps.getInt("isopen");
                                        //如果开关是开启状态
                                        if (isOpen == 1){
                                            int subcompanyName = -1;		//分部id
                                            subcompanyName =user.getUserSubCompany1();//获取该用户的分部id

                                            RecordSet sub = new RecordSet();
                                            *//*String Docsql = "select subcompanyname from uf_wps where subcompanyname in ("+subcompanyName+")";*//*
                                            String Docsql = "select * from uf_wps where instr(',' || subcompanyname || ',', ',' || " + subcompanyName+ " || ',')>0";
                                            new BaseBean().writeLog("(canEditAcc是否显示编辑按钮)" + Docsql);
                                            sub.executeQuery(Docsql);
                                            if (sub.next()){
                                                isOpenWps = true;
                                            }
                                        }
                                    }*/
                                }catch (Exception e){
                                    new BaseBean().writeLog("canEditAcc报错" + e);
                                }
/*
                                new BaseBean().writeLog("canEditAcc（权限）"+user.getUID()+" | " + filename + " | " + isOpenWps);
*/
                                docAttr.put("canEditAcc",isOpenWps);
                            }

                            String ddcode = SystemDocUtil.takeddcode(user,docImageManager.getImagefileid(),null);
                            docAttr.put("ddcode","");

                            request.getSession().setAttribute("docAttr_" + user.getUID() + "_" + docAttr.get("docid") + "_" + docAttr.get("imagefileid"),"1");
                        }
                        int attLength = docAttrs.size();
                        docInfo.put("canOpenSingleAttach",canOpenSingleAttach && isContentEmpty && attLength>0);//是否开启单附件打开
                        docInfo.put("isContentEmpty",isContentEmpty && attLength>0 && canOpenSingleAttach); //正文内容是否为空

                        if(canEditDoc && (Boolean)docInfo.get("canOpenSingleAttach") && (Boolean)docInfo.get("isContentEmpty")){
                            if(docAttrs.size() > 0){
                                Map<String,Object> docAttr = docAttrs.get(0);
                                String filename = Util.null2String(docAttr.get("filename"));
                                new BaseBean().writeLog("(filename)" + filename + " | " + canEditDoc);
                                if(SystemDocUtil.canEditForMobile(filename,user)){
                                    if(doctype == 2){
                                        docInfo.put("canEditDoc",true);
                                    }else{
                                        docInfo.put("canEditAcc",true);
                                    }
                                }
                            }
                        }

                        //获取系统文档相关配置
                        //点击文档附件列表，是否弹出下载还是预览的提示框
                        docInfo.put("doc_acc_isalert_modal",Util.null2s(bb.getPropValue("doc_mobile_detail_prop","doc_acc_isalert_modal"),"0"));
                        //是否展示文档最后修改时间
                        docInfo.put("doc_final_edit",SystemDocUtil.getDefaultSet("doc_final_edit"));
                        //是否显示阅读的、评论的、点赞的、附件的数量
                        docInfo.put("doc_given_count",SystemDocUtil.getDefaultSet("doc_given_count"));
                        //不可在线预览的附件是否可以下载
                        docInfo.put("doc_acc_isdownload",Util.null2s(bb.getPropValue("doc_mobile_detail_prop","doc_acc_isdownload"),"1"));
                        //# 移动端是否直接下载，不提示弹框 0 不提示，1提示
                        docInfo.put("doc_acc_download_noalert",Util.null2s(bb.getPropValue("doc_mobile_detail_prop","doc_acc_download_noalert"),"1"));
                        docInfo.put("docid",docid);
                        docInfo.put("loginusername",user.getLastname());
                        docInfo.put("docidEncode",des.encrypt(docid+""));
                        boolean secretFlag = CheckPermission.isOpenSecret();
                        String secretLevel = DocSecretLevelUtil.takeSecretLevelbyDocid(docid+"");
                        docInfo.put("openSecret",secretFlag);
                        docInfo.put("secretLevel",secretLevel);
                        docInfo.put("secretValidity",DocSecretLevelUtil.takeSecretLevelValue(secretLevel,user,docid+"",true));
                        docInfo.put("secretValidityLevel1",DocSecretLevelUtil.takeSecretLevelValue("1",user,"",true));
                        docInfo.put("secretValidityLevel2",DocSecretLevelUtil.takeSecretLevelValue("2",user,"",true));
                        String  isUseEMessager = "0";
                        String shareDoc = ValveConfigManager.getTypeValve("share", "shareDoc");//service.get(new HashMap<String,Object>(), user);
                        if("1".equals(shareDoc)){
                            isUseEMessager = "1";
                        }
                        docInfo.put("isUseEMessager",isUseEMessager);
                        apidatas.put("docInfo",docInfo);
                        apidatas.put("canReader",canReader);
                        apidatas.put("docisLock",docisLock);
                        apidatas.put("api_status",true);
                        apidatas.put("msg","success");
                        Map<String,Object> secWmSetMap = WaterMarkUtil.getCategoryWmSet(docid+"",false);
                        apidatas.put("secWmSet",secWmSetMap);
                        mainFileInfo.put("mainFileid",officeImagefileid.isEmpty()?mainFileid:officeImagefileid);
                        mainFileInfo.put("mainFileOnline",mainFileOnline);
                        mainFileInfo.put("ficon",DocIconUtil.getDocIconDetail(mainext));
                        apidatas.put("mainFileInfo",mainFileInfo);

                    }else{
                        apidatas.put("api_status",true);
                        apidatas.put("canReader",canReader);
                        apidatas.put("docisLock",docisLock);
                        apidatas.put("msg","canReader is false");
                    }
                }
            }
        }catch (Exception ex){
            ex.printStackTrace();
            apidatas.put("api_status", false);
            apidatas.put("msg", "error");
            //记录异常日志
            writeLog("GetDocDetailCmd--->:"+ ex.getMessage());
        }
        return apidatas;
    }
    /**
     * 替换掉link中的ip地址
     * @return
     */
    public String replaceip(String content){
        List<String> lists = new ArrayList<String>();
        List<String> resultLists = new ArrayList<String>();
        if(content == null || content.isEmpty()) return content;
//        String reg = "<img(.*?)/weaver/weaver.file.FileDownload";
        String reg = "<link(.*?)/cloudstore/resource";
        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(content);
        while(matcher.find()){
            lists.add(matcher.group());
        }
        for (int i=0;i<lists.size();i++){
            String liststr = lists.get(i);
            content = content.replaceAll(liststr,"<link href=\"/cloudstore/resource");
        }
        return content;
    }
}
