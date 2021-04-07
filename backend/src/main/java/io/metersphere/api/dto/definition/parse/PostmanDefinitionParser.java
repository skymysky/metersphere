package io.metersphere.api.dto.definition.parse;

import com.alibaba.fastjson.JSON;
import io.metersphere.api.dto.ApiTestImportRequest;
import io.metersphere.api.dto.definition.request.sampler.MsHTTPSamplerProxy;
import io.metersphere.api.dto.parse.postman.PostmanCollection;
import io.metersphere.api.dto.parse.postman.PostmanItem;
import io.metersphere.api.dto.parse.postman.PostmanKeyValue;
import io.metersphere.api.parse.PostmanAbstractParserParser;
import io.metersphere.base.domain.ApiDefinitionWithBLOBs;
import io.metersphere.base.domain.ApiModule;
import io.metersphere.base.domain.ApiTestCaseWithBLOBs;
import io.metersphere.base.domain.Project;
import io.metersphere.base.mapper.ProjectMapper;
import io.metersphere.commons.utils.BeanUtils;
import io.metersphere.commons.utils.CommonBeanFactory;

import java.io.InputStream;
import java.util.*;

public class PostmanDefinitionParser extends PostmanAbstractParserParser<ApiDefinitionImport> {

    @Override
    public ApiDefinitionImport parse(InputStream source, ApiTestImportRequest request) {
        String testStr = getApiTestStr(source);
        this.projectId = request.getProjectId();
        PostmanCollection postmanCollection = JSON.parseObject(testStr, PostmanCollection.class);
        List<PostmanKeyValue> variables = postmanCollection.getVariable();
        ApiDefinitionImport apiImport = new ApiDefinitionImport();
        List<ApiDefinitionWithBLOBs> results = new ArrayList<>();
        List<ApiTestCaseWithBLOBs> cases = new ArrayList<>();
        Map<String, String> repeatMap = new HashMap();
        ProjectMapper projectMapper = CommonBeanFactory.getBean(ProjectMapper.class);
        Project project = projectMapper.selectByPrimaryKey(request.getProjectId());
        parseItem(postmanCollection.getItem(), variables, results,
                ApiDefinitionImportUtil.buildModule(ApiDefinitionImportUtil.getSelectModule(request.getModuleId()),
                        postmanCollection.getInfo().getName(), this.projectId), cases, repeatMap, project.getRepeatable());
        apiImport.setData(results);
        apiImport.setCases(cases);
        return apiImport;
    }

    protected void parseItem(List<PostmanItem> items, List<PostmanKeyValue> variables, List<ApiDefinitionWithBLOBs> results,
                             ApiModule parentModule, List<ApiTestCaseWithBLOBs> cases, Map<String, String> repeatMap, Boolean repeatable) {
        for (PostmanItem item : items) {
            List<PostmanItem> childItems = item.getItem();
            if (childItems != null) {
                ApiModule module = null;
                module = ApiDefinitionImportUtil.buildModule(parentModule, item.getName(), this.projectId);
                parseItem(childItems, variables, results, module, cases, repeatMap, repeatable);
            } else {
                MsHTTPSamplerProxy msHTTPSamplerProxy = parsePostman(item);
                ApiDefinitionWithBLOBs request = buildApiDefinition(msHTTPSamplerProxy.getId(), msHTTPSamplerProxy.getName(),
                        msHTTPSamplerProxy.getPath(), msHTTPSamplerProxy.getMethod(), new ApiTestImportRequest());
                request.setPath(msHTTPSamplerProxy.getPath());
                request.setRequest(JSON.toJSONString(msHTTPSamplerProxy));
                if (parentModule != null) {
                    request.setModuleId(parentModule.getId());
                }
                if (request != null) {
                    if (repeatMap.keySet().contains(request.getMethod() + request.getPath())
                            && (repeatable == null || repeatable == false)) {
                        ApiTestCaseWithBLOBs apiTestCase =  new ApiTestCaseWithBLOBs();
                        BeanUtils.copyBean(apiTestCase, request);
                        apiTestCase.setApiDefinitionId(repeatMap.get(request.getMethod() + request.getPath()));
                        apiTestCase.setPriority("P0");
                        cases.add(apiTestCase);
                    } else {
                        repeatMap.put(request.getMethod() + request.getPath(), request.getId());
                        results.add(request);
                    }
                }
            }
        }
    }
}
