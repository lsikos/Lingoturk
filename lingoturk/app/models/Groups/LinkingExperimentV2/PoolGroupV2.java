package models.Groups.LinkingExperimentV2;


import com.amazonaws.mturk.requester.HIT;
import com.amazonaws.mturk.service.axis.RequesterService;
import com.fasterxml.jackson.databind.JsonNode;
import controllers.Application;
import models.Groups.AbstractGroup;
import models.LingoExpModel;
import models.Questions.LinkingExperimentV1.Item;
import models.Questions.LinkingExperimentV1.Script;
import models.Questions.PartQuestion;
import models.Questions.Question;
import models.Repository;
import models.Worker;
import play.mvc.Result;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.persistence.Basic;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import static play.mvc.Results.ok;

@Entity
@Inheritance
@DiscriminatorValue("PoolGroupV2")
public class PoolGroupV2 extends AbstractGroup {

    @Basic
    String fileName;

    public PoolGroupV2(String fileName){
        this.fileName = fileName;
    }

    List<Script> description = null;

    public PoolGroupV2(){}

    @Override
    public String publishOnAMT(RequesterService service, int publishedId, String hitTypeId, Long lifetime, Integer maxAssignmentsPerCombination) throws SQLException {
        String url = null;
        hitTypeId = service.registerHITType(7200L, 600L, 0.15, "Linking most similar English sentences. #" + Application.actCounter, "script,scripts,activity,activities,linking,aligning, English,events,description,descriptions,most,similar,everyday","We require native speakers of English who will be presented with two descriptions of everyday activities and will be required to decide if the highlighted event in the first description is most similar to the highlighted event in the second description.", Application.qualificationRequirements);

        for(Script lhs_script : getDescription()){
            for(Item lhs_item : lhs_script.getItemList()){
                if(!(lhs_item.getH().equals("") || lhs_item.getH().equals("1"))){
                    String rhs_scriptId = lhs_item.getH().split(",")[0].trim();
                    int rhs_SlotId = Integer.parseInt(lhs_item.getH().split(",")[1].trim());

                    for(Script rhs_script : getDescription()){
                        if(rhs_script.getScriptId().equals(rhs_scriptId)){
                            String question = "<ExternalQuestion xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/ExternalQuestion.xsd\">"
                                    + "<ExternalURL> " + Application.getStaticIp() + "/renderAMT?id=" + lhs_script.getId() + "&amp;id2=" + rhs_script.getId() + "&amp;slot1=" + lhs_item.getSlot() + "&amp;slot2=" + rhs_SlotId + "</ExternalURL>"
                                    + "<FrameHeight>" + 800 + "</FrameHeight>" + "</ExternalQuestion>";

                            HIT hit = service.createHIT(hitTypeId, null, null, null, question, null, null, null, lifetime, maxAssignmentsPerCombination, null, null, null, null, null, null);

                            url = service.getWebsiteURL() + "/mturk/preview?groupId=" + hit.getHITTypeId();
                            System.out.println(url);
                            insert(hit.getHITId(),publishedId,lhs_script.getId(),rhs_script.getId());
                        }
                    }
                }
            }
        }

        return url;
    }

    public void insert(String hitID,int publishedId, int question1, int question2) throws SQLException {
        PreparedStatement statement = Repository.getConnection().prepareStatement("INSERT INTO PartPublishedAs(PartID,mTurkID,publishedId,question1,question2) VALUES(?,?,?,?,?)");
        statement.setInt(1, getId());
        statement.setString(2, hitID);
        statement.setInt(3,publishedId);
        statement.setInt(4,question1);
        statement.setInt(5,question2);
        statement.execute();
    }

    public JsonObject returnJSON() throws SQLException {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
        for(Question q : description){
            arrayBuilder.add(q.returnJSON());
        }
        objectBuilder.add("description", arrayBuilder.build()).build();

        return objectBuilder.build();
    }

    public List<Script> getDescription() throws SQLException {
        if(description != null){
            return description;
        }

        List<Script> tmp = new LinkedList<>();
        for(PartQuestion s : getQuestions()){
            Script s_tmp = (Script) s;
                tmp.add(s_tmp);
        }
        return tmp;
    }


    @Override
    public void setJSONData(LingoExpModel experiment, JsonNode partNode) throws SQLException {
        try{
            String fileName = partNode.get("fileName").asText();
            System.out.println(fileName);

            String descriptionJson = partNode.get("description").asText();
            List<Script> descriptionList = Script.createScripts(descriptionJson,null,experiment,false);

            questions = new LinkedList<>(descriptionList);
        }catch (Throwable t){
            t.printStackTrace();
        }
    }

    @Override
    public Result getRandomQuestion(String assignmentId, String hitId, String workerId, String turkSubmitTo, LingoExpModel exp) throws SQLException {
        for(Script lhs_script : getDescription()){
            for(Item lhs_item : lhs_script.getItemList()){
                if(!(lhs_item.getH().equals("") || lhs_item.getH().equals("1"))){
                    String rhs_scriptId = lhs_item.getH().split(",")[0].trim();
                    int rhs_SlotId = Integer.parseInt(lhs_item.getH().split(",")[1].trim());

                    for(Script rhs_script : getDescription()){
                        if(rhs_script.getScriptId().equals(rhs_scriptId)){
                                    return PoolGroupV2.renderAMT(lhs_script.getId(),rhs_script.getId(),lhs_item.getSlot(),rhs_SlotId,assignmentId,hitId,workerId,turkSubmitTo);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Result render(Worker worker, String assignmentId, String hitId, String workerId, String turkSubmitTo, LingoExpModel exp) throws SQLException {
        return this.getRandomQuestion(assignmentId,hitId,workerId,turkSubmitTo,exp);
    }

    public static Result renderAMT(int questionId, int questionId2, int slot1, int slot2, String assignmentId, String hitId, String workerId, String turkSubmitTo){
        return ok(views.html.renderExperiments.LinkingExperimentV2.linkingExperimentV2.render(questionId,slot1,questionId2,slot2,assignmentId, hitId, workerId,
                turkSubmitTo));
    }


}