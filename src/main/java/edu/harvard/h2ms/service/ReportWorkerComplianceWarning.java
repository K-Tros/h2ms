package edu.harvard.h2ms.service;

import edu.harvard.h2ms.domain.core.Event;
import edu.harvard.h2ms.domain.core.Question;
import edu.harvard.h2ms.domain.core.User;
import edu.harvard.h2ms.exception.InvalidAnswerTypeException;
import edu.harvard.h2ms.repository.QuestionRepository;
import edu.harvard.h2ms.repository.UserRepository;
import edu.harvard.h2ms.service.utils.H2msRestUtils;
import edu.harvard.h2ms.service.utils.ReportUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ReportWorkerComplianceWarning implements ReportWorker {

  final Logger log = LoggerFactory.getLogger(ReportWorkerComplianceWarning.class);

  @Autowired private EventService eventService;

  @Autowired private QuestionRepository questionRepository;

  @Autowired private UserRepository userRepository;

  @Override
  public String getType() {
    return "complianceWarning";
  }

  public String createReport() {
    List<Event> events = new ArrayList<>();

    // for all questions:
    Map<Question, Map<User, Double>> allComplianceResult = new HashMap<>();
    Hibernate.initialize(questionRepository);
    for (Question question : questionRepository.findAll()) {
      Map<User, Double> complianceResult = new HashMap<>();

      try {
        events = eventService.findEventsForCompliance(question);

        // get compliance rates for all users:
        for (User user : userRepository.findAll()) {
          complianceResult.put(
              user,
              H2msRestUtils.calculateCompliance(
                  question,
                  events
                      .stream()
                      .filter(event -> event.getSubject().equals(user))
                      .collect(Collectors.toSet())));
        }

        allComplianceResult.put(question, complianceResult);

      } catch (InvalidAnswerTypeException e) {
        log.debug("*********skipping");
      }
    }

    // Create data for CSV-like string output
    List<List<String>> data = new ArrayList<List<String>>();
    List<String> row = new ArrayList<String>();
    for (Question question : allComplianceResult.keySet()) {
      String questionString = question.getQuestion();
      Map<User, Double> complianceMap = allComplianceResult.get(question);
      for (User user : complianceMap.keySet()) {
        String userEmail = user.getEmail();
        String complianceRate = complianceMap.get(user).toString();
        row.add(questionString);
        row.add(userEmail);
        row.add(complianceRate);
        data.add(row);
      }
    }

    return ReportUtils.writeCsvString(data);
  }

  public boolean isTriggered() {

    return true;
  }
}
