package models.Groups;

import com.amazonaws.mturk.service.axis.RequesterService;
import com.fasterxml.jackson.databind.JsonNode;
import models.LingoExpModel;
import models.Questions.PartQuestion;
import models.Questions.PublishableQuestion;
import models.Questions.Question;
import models.Questions.QuestionFactory;
import models.Repository;
import models.Worker;
import play.db.ebean.Model;
import play.mvc.Result;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.persistence.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;


@Entity
@Inheritance
@DiscriminatorColumn(length=30)
@Table(name = "Groups")
@DiscriminatorValue("DistinctGroup")
public abstract class AbstractGroup extends Model {

    @Id
    @Column(name = "PartId")
    int id;

    @Basic
    int availability;

    @Basic
    protected
    String fileName;

    @Basic
    int number;

    private static Finder<Integer, AbstractGroup> finder = new Finder<>(Integer.class, AbstractGroup.class);

    private Random random = new Random();

    protected List<PartQuestion> questions = null;

    private static ReentrantLock lock = new ReentrantLock();

    public AbstractGroup(){}

    public abstract String publishOnAMT(RequesterService service, int publishedId, String hitTypeId, Long lifetime, Integer maxAssignments) throws SQLException;

    public boolean decreaseIfAvailable() throws SQLException {
        boolean answer = false;
        lock.lock();
        int availability;
        try{
            availability = getAvailability();
            if(availability > 0){
                setAvailability(availability - 1);
                answer = true;
            }else{
                answer = false;
            }
        }finally{
            lock.unlock();
        }

        System.out.println("Part " + getId() + " availability: " + (availability) + " -> return " + answer);
        return answer;
    }

    public int getAvailability() throws SQLException {
        PreparedStatement statement = Repository.getConnection().prepareStatement("SELECT availability FROM Parts WHERE PartId=" + this.getId());
        ResultSet rs = statement.executeQuery();

        int result = -1;

        if (rs.next()) {
            result = rs.getInt("availability");
        }

        return result;
    }

    public void setAvailability(int availability) throws SQLException {
        PreparedStatement statement = Repository.getConnection().prepareStatement("UPDATE Parts SET availability = ? WHERE PartId=" + this.getId());
        statement.setInt(1,availability);
        statement.execute();
    }

    public void insert(String hitID,int publishedId) throws SQLException {
        PreparedStatement statement = Repository.getConnection().prepareStatement("INSERT INTO PartPublishedAs(PartID,mTurkID,publishedId) VALUES(?,?,?)");
        statement.setInt(1, getId());
        statement.setString(2, hitID);
        statement.setInt(3,publishedId);
        statement.execute();
    }

    public PartQuestion getNextQuestion() throws SQLException {
        List<PartQuestion> questions = getQuestions();
        for(PartQuestion question : questions){
            PartQuestion actQuestion = (PartQuestion) question.getIfAvailable();
            if(actQuestion != null){
                return actQuestion;
            }
        }
        return null;
    }

    public void setJSONData(LingoExpModel experiment, JsonNode partNode) throws SQLException {
        List<PartQuestion> questions_tmp = new LinkedList<>();
        // Create Questions
        for(Iterator<JsonNode> questions =  partNode.get("questions").iterator(); questions.hasNext(); ){
            JsonNode questionNode = questions.next();
            questions_tmp.add(QuestionFactory.createQuestion(questionNode.get("type").asText(),experiment,questionNode));
        }
        this.questions = questions_tmp;
    }

    public static AbstractGroup byId(int id) {
        return finder.byId(id);
    }

    public void addExperimentUsedIn(LingoExpModel exp) throws SQLException {
        PreparedStatement statement = Repository.getConnection().prepareStatement(
                "INSERT INTO LingoExpModels_contain_Parts(LingoExpModelID,PartID) " +
                        "SELECT " + exp.getId() + ", " + this.getId() +
                        " WHERE NOT EXISTS (" +
                        "SELECT * FROM LingoExpModels_contain_Parts WHERE LingoExpModelID=" + exp.getId() + " AND PartID=" + this.getId() +
                        ")");

        statement.execute();
    }

    public Result render(Worker worker, String assignmentId, String hitId, String workerId, String turkSubmitTo, LingoExpModel exp) throws SQLException {
        Worker.Participation participation = worker.getParticipatesInPart(this);

        if(!assignmentId.equals("ASSIGNMENT_ID_NOT_AVAILABLE_TEST")){
            // Worker hasn't participated in the HIT already
            if(participation == null){
                Question question = this.getNextQuestion();
                worker.addParticipatesInPart(this, question, null, assignmentId, hitId);
                return question.render(assignmentId,hitId,workerId,turkSubmitTo,exp.getAdditionalExplanations());
            }
            // Worker has already participated in a hit
            if (participation.getAssignmentID().equals(assignmentId)){
                return PublishableQuestion.byId(participation.getQuestionID()).render(assignmentId,hitId,workerId,turkSubmitTo,exp.getAdditionalExplanations());
            }
        }

        // just a test -> return random question
        return this.getRandomQuestion(assignmentId,hitId,workerId,turkSubmitTo,exp);
    }

    public Result getRandomQuestion(String assignmentId, String hitId, String workerId, String turkSubmitTo, LingoExpModel exp) throws SQLException {
        int nr = random.nextInt(getQuestions().size());
        return getQuestions().get(nr).render(assignmentId, hitId, workerId, turkSubmitTo, exp.getAdditionalExplanations());
    }

    public JsonObject returnJSON() throws SQLException {
        JsonObjectBuilder partBuilder = Json.createObjectBuilder();

        partBuilder.add("newId", "");
        partBuilder.add("newSentence1", "");
        partBuilder.add("newSentence2", "");
        partBuilder.add("newConnectives", "");

        JsonArrayBuilder questionsBuilder = Json.createArrayBuilder();

        for (PartQuestion partQuestion : getQuestions()) {
            questionsBuilder.add(partQuestion.returnJSON());
        }

        partBuilder.add("questions", questionsBuilder.build());

        return partBuilder.build();
    }

    public Integer getExperimentUsedIn() throws SQLException {
        PreparedStatement statement = Repository.getConnection().prepareStatement("SELECT * FROM LingoExpModels_contain_Parts WHERE PartID=" + this.getId());
        ResultSet rs = statement.executeQuery();

        int result = -1;
        if (rs.next()) {
            result = rs.getInt("LingoExpModelID");
        }
        return result;
    }

    public List<PartQuestion> getQuestions() throws SQLException {
        if (questions == null){
            PreparedStatement statement = Repository.getConnection().prepareStatement("SELECT * FROM Parts_contain_Questions WHERE PartID=" + this.getId());
            ResultSet rs = statement.executeQuery();

            List<PartQuestion> result = new LinkedList<PartQuestion>();
            while (rs.next()) {
                result.add((PartQuestion) Question.byId(rs.getInt("QuestionID")));
            }
            this.questions = result;
            return result;
        }

        return this.questions;
    }

    public void saveQuestions() throws SQLException {
        PreparedStatement statement = Repository.getConnection().prepareStatement("INSERT INTO Parts_contain_Questions(PartID,QuestionID) SELECT " + getId() + ", ? " +
                "WHERE NOT EXISTS (" +
                "SELECT * FROM Parts_contain_Questions WHERE PartID= " + getId() + " AND QuestionID= ? " +
                ")");

        for (Question question : questions) {
            statement.setInt(1, question.getId());
            statement.setInt(2, question.getId());
            statement.execute();
        }
    }

    @Override
    public void delete(){
        try {
            for(Question question : getQuestions()){
                question.delete();
                super.delete();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "Id: " + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractGroup)) return false;

        AbstractGroup group = (AbstractGroup) o;

        return id == group.id;

    }

    @Override
    public int hashCode() {
        return id;
    }

}