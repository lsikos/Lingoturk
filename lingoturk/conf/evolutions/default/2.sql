﻿# Additional tables,functions

# --- !Ups

CREATE SEQUENCE LingoExpModelPublished_Seq START 1;
CREATE SEQUENCE ErrorMessages_Seq START 1;
CREATE SEQUENCE PictureNamingMailAddress_Seq START 1;

CREATE TABLE pendingAssignments(
	assignmentID varchar PRIMARY KEY
);

CREATE TABLE Workers_participateIn_Parts(
	WorkerID varchar,
	PartID int,
	assignmentID varchar,
	hitID varchar,
	QuestionID int,
	QuestionID2 int,
	verified bool DEFAULT false,
	timestamp timestamp DEFAULT now(),
	PRIMARY KEY (WorkerID,assignmentID),
	FOREIGN KEY (WorkerID) REFERENCES Workers ON DELETE CASCADE
);

CREATE TABLE Workers_areBlockedFor_LingoExpModels(
	WorkerID varchar,
	LingoExpModelID int,
	PRIMARY KEY (WorkerID,LingoExpModelID),
	FOREIGN KEY (LingoExpModelID) REFERENCES LingoExpModels ON DELETE CASCADE,
	FOREIGN KEY (WorkerID) REFERENCES Workers ON DELETE CASCADE

);

CREATE TABLE LingoExpModels_contain_Parts(
	LingoExpModelID int,
	PartID int,
	PRIMARY KEY (LingoExpModelID,PartID),
	FOREIGN KEY (LingoExpModelID) REFERENCES LingoExpModels ON DELETE CASCADE,
	FOREIGN KEY (PartID) REFERENCES Groups ON DELETE CASCADE
);

CREATE TABLE Parts_contain_Questions(
	PartID int,
	QuestionID int,
	PRIMARY KEY (QuestionID,PartID),
	FOREIGN KEY (QuestionID) REFERENCES Questions ON DELETE CASCADE,
	FOREIGN KEY (PartID) REFERENCES Groups ON DELETE CASCADE
);

CREATE TABLE LingoExpModels_contain_CheaterDetectionQuestions(
	LingoExpModelID int,
	QuestionID int,
	PRIMARY KEY (LingoExpModelID,QuestionID),
	FOREIGN KEY (LingoExpModelID) REFERENCES LingoExpModels ON DELETE CASCADE,
	FOREIGN KEY (QuestionID) REFERENCES Questions ON DELETE CASCADE
);

CREATE TABLE LingoExpModels_contain_ExampleQuestions(
	LingoExpModelID int,
	QuestionID int,
	PRIMARY KEY (LingoExpModelID,QuestionID),
	FOREIGN KEY (LingoExpModelID) REFERENCES LingoExpModels ON DELETE CASCADE,
	FOREIGN KEY (QuestionID) REFERENCES Questions ON DELETE CASCADE
);

CREATE TABLE LingoExpModelPublishedAs (
	publishID int DEFAULT nextval('LingoExpModelPublished_Seq') PRIMARY KEY,
	LingoExpModelID int REFERENCES LingoExpModels ON DELETE CASCADE,
	timestamp timestamp DEFAULT current_timestamp,
	lifetime bigint,
	url varchar,
	destination varchar
);

CREATE TABLE PictureNamingMailAddress(
	id int DEFAULT nextval('PictureNamingMailAddress_Seq') PRIMARY KEY,
	WorkerId VARCHAR(60) NOT NULL,
	timestamp timestamp DEFAULT now(),
	mailAddress VARCHAR(100)
);

CREATE TABLE QuestionPublishedAs(
	publishedId int REFERENCES LingoExpModelPublishedAs ON DELETE CASCADE,
	QuestionID int REFERENCES Questions ON DELETE CASCADE,
	mTurkID varchar,
	PRIMARY KEY(mTurkID)
);

CREATE TABLE participatesInCD_Question(
	QuestionID int REFERENCES Questions ON DELETE CASCADE,
	WorkerID varchar,
	assignmentID varchar,
	answerCorrect boolean,
	PRIMARY KEY(assignmentID,WorkerID),
	UNIQUE(QuestionID,WorkerID)
);

CREATE TABLE failedAssignments(
	assignmentID varchar PRIMARY KEY,
	timestamp timestamp DEFAULT current_timestamp
);


CREATE TABLE PartPublishedAs(
	publishedId int REFERENCES LingoExpModelPublishedAs ON DELETE CASCADE,
	timestamp timestamp DEFAULT now(),
	PartID int REFERENCES Groups ON DELETE CASCADE,
	mTurkID varchar,
	question1 int,
	question2 int,
	PRIMARY KEY(mTurkID)
);

CREATE TABLE ErrorMessages(
	id int DEFAULT nextval('ErrorMessages_Seq') PRIMARY KEY,
	timestamp timestamp DEFAULT now(),
	message VARCHAR NOT NULL
);

CREATE FUNCTION publishLingoExpModel(int,bigint,varchar,varchar) RETURNS int AS $$
	BEGIN
		INSERT INTO LingoExpModelPublishedAs(LingoExpModelID,lifetime,url,destination) VALUES ($1,$2,$3,$4);;
		RETURN currval('LingoExpModelPublished_Seq');;
	END;;
$$ LANGUAGE PLPGSQL;

CREATE FUNCTION getScriptId(lhs_script integer) RETURNS integer AS $$
	DECLARE
		nr int;;
  BEGIN
		SELECT LinkingV1_scriptId INTO nr FROM Questions WHERE QuestionId = lhs_script;;
    RETURN nr;;
  END;;
$$ LANGUAGE plpgsql;

CREATE FUNCTION getItemSlot(itemId integer) RETURNS integer AS $$
	DECLARE
		nr int;;
  BEGIN
		SELECT slot INTO nr FROM LinkingItem WHERE id = itemId;;
    RETURN nr;;
  END;;
$$ LANGUAGE plpgsql;

CREATE FUNCTION getC(itemId integer) RETURNS varchar(255) AS $$
	DECLARE
		answer varchar(255);;
  BEGIN
		SELECT c INTO answer FROM LinkingItem WHERE id = itemId;;
    RETURN answer;;
  END;;
$$ LANGUAGE plpgsql;

CREATE FUNCTION getScenarioName(id integer) RETURNS varchar(125) AS $$
	DECLARE
		n VARCHAR(125);;
  BEGIN
		SELECT fileName INTO n FROM parts_contain_questions JOIN Groups USING (PartId) WHERE QuestionId = id;;
    RETURN n;;
  END;;
$$ LANGUAGE plpgsql;

CREATE FUNCTION getStandart(lhs_item integer,rhs_script_id int) RETURNS int AS $$
	DECLARE
		s int;;
  BEGIN
		SELECT slot INTO s FROM Pair WHERE item_id = lhs_item AND sentence_id = rhs_script_id;;
    RETURN s;;
  END;;
$$ LANGUAGE plpgsql;

CREATE FUNCTION wrongAnswersCount(wId varchar) RETURNS integer AS $$
	DECLARE
		nr int;;
	BEGIN
		SELECT count(*) INTO nr FROM (
		SELECT scenarioName,scenarioName,lhs_script_id,rhs_script_id,lhs_item_slot,
		CASE WHEN noLinkingPossible THEN '-1' WHEN rhs_item_slot IS NOT NULL THEN rhs_item_slot || '' WHEN before_slot IS NULL THEN 'after_' || after_slot WHEN after_slot IS NULL THEN 'before_' || before_slot ELSE 'between_' || after_slot || '_' || before_slot END
		AS result, goldenStandart || '' AS goldenStandart FROM (
		SELECT DISTINCT * FROM (SELECT getScenarioName(lhs_script) AS scenarioName,getScriptId(lhs_script) AS lhs_script_id,
			getScriptId(rhs_script) AS rhs_script_id,getItemSlot(lhs_item) AS lhs_item_slot,getItemSlot(rhs_item) AS rhs_item_slot,
			getItemSlot(before) AS before_slot,getItemSlot(after) AS after_slot,noLinkingPossible,getStandart(lhs_item,getScriptId(rhs_script)) AS goldenStandart,workerId
		FROM LinkingV3Results) AS tmp
		WHERE goldenStandart IS NOT NULL AND workerId = wId
		ORDER BY scenarioName,lhs_script_id,rhs_script_id,lhs_item_slot,rhs_item_slot) as tmp2) as tmp3
		WHERE result != goldenstandart;;

		RETURN nr;;
	END;;
$$ LANGUAGE plpgsql;

# --- !Downs

DROP SEQUENCE IF EXISTS LingoExpModelPublished_Seq CASCADE;
DROP SEQUENCE IF EXISTS ErrorMessages_Seq CASCADE;
DROP SEQUENCE IF EXISTS PictureNamingMailAddress_Seq CASCADE;
DROP TABLE IF EXISTS ErrorMessages;
DROP TABLE IF EXISTS Workers_participateIn_Parts CASCADE;
DROP TABLE IF EXISTS Workers_areBlockedFor_LingoExpModels CASCADE;
DROP TABLE IF EXISTS LingoExpModels_contain_Parts CASCADE;
DROP TABLE IF EXISTS Parts_contain_Questions CASCADE;
DROP TABLE IF EXISTS LingoExpModels_contain_CheaterDetectionQuestions CASCADE;
DROP TABLE IF EXISTS LingoExpModels_contain_ExampleQuestions CASCADE;
DROP TABLE IF EXISTS LingoExpModelPublishedAs CASCADE;
DROP TABLE IF EXISTS QuestionPublishedAs CASCADE;
DROP TABLE IF EXISTS participatesInCD_Question CASCADE;
DROP TABLE IF EXISTS pendingAssignments;
DROP TABLE IF EXISTS failedAssignments;
DROP TABLE IF EXISTS PartPublishedAs;
DROP TABLE IF EXISTS PictureNamingMailAddress;
DROP FUNCTION IF EXISTS publishLingoExpModel(int,bigint,varchar,varchar);
DROP FUNCTION IF EXISTS wrongAnswersCount(varchar);
DROP FUNCTION IF EXISTS getStandart(int,int);
DROP FUNCTION IF EXISTS getScenarioName(int);
DROP FUNCTION IF EXISTS getItemSlot(int);
DROP FUNCTION IF EXISTS getScriptId(int);
DROP FUNCTION IF EXISTS getC(int);