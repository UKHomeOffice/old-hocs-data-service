package uk.gov.digital.ho.hocs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.digital.ho.hocs.dto.legacy.topics.TopicListEntityRecord;
import uk.gov.digital.ho.hocs.dto.legacy.units.UnitCreateRecord;
import uk.gov.digital.ho.hocs.dto.legacy.units.UnitRecord;
import uk.gov.digital.ho.hocs.dto.legacy.users.UserCreateRecord;
import uk.gov.digital.ho.hocs.legacy.CSVList;
import uk.gov.digital.ho.hocs.legacy.topics.CSVTopicLine;
import uk.gov.digital.ho.hocs.legacy.topics.DCUFileParser;
import uk.gov.digital.ho.hocs.legacy.topics.UKVIFileParser;
import uk.gov.digital.ho.hocs.legacy.units.CSVUnitLine;
import uk.gov.digital.ho.hocs.legacy.units.UnitFileParser;
import uk.gov.digital.ho.hocs.legacy.users.CSVUserLine;
import uk.gov.digital.ho.hocs.legacy.users.UserFileParser;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LegacyService {

    private final ListRepository repo;

    @Autowired
    public LegacyService(ListRepository repo) {
        this.repo = repo;
    }

    @Cacheable(value = "legacylist", key = "#name")
    public TopicListEntityRecord[] getLegacyTopicListByName(String name) throws ListNotFoundException {
        try {
            DataList list = repo.findOneByName(name);
            return TopicListEntityRecord.asArray(list);
        } catch (NullPointerException e) {
            throw new ListNotFoundException();
        }
    }

    private static DataList createTopicsListFromCSV(CSVList list, String listName, String caseType) {
        Map<String, Set<DataListEntity>> topics = new HashMap<>();

        List<CSVTopicLine> lines = list.getLines();
        for (CSVTopicLine line : lines) {
            topics.putIfAbsent(line.getParentTopicName(), new HashSet<>());
            Set<DataListEntityProperty> properties = new HashSet<>();
            properties.add(new DataListEntityProperty("topicUnit", line.getTopicUnit()));
            if (!line.getTopicTeam().isEmpty()) {
                properties.add(new DataListEntityProperty("topicTeam", line.getTopicTeam()));
            }
            DataListEntity dle = new DataListEntity(line.getTopicName(), line.getTopicValue(caseType));
            dle.setProperties(properties);
            topics.get(line.getParentTopicName()).add(dle);
        }

        Set<DataListEntity> dataListEntities = new HashSet<>();
        for (Map.Entry<String, Set<DataListEntity>> entity : topics.entrySet()) {
            String parentTopic = entity.getKey();

            Set<DataListEntityProperty> properties = new HashSet<>();
            properties.add(new DataListEntityProperty("caseType", caseType));

            DataListEntity dle = new DataListEntity(parentTopic, parentTopic);
            dle.setSubEntities(entity.getValue());
            dle.setProperties(properties);
            dataListEntities.add(dle);
        }

        return new DataList(listName, dataListEntities);
    }

    // Below this line are not @Caching because they are 'create' scripts, likely only called once.

    @Caching(evict = {
            @CacheEvict(value = "list", key = "#listName", beforeInvocation = true),
            @CacheEvict(value = "legacyList", key = "#listName", beforeInvocation = true)})
    public DataList createDCUTopicsListFromCSV(MultipartFile file, String listName, String caseType) {
        return createTopicsListFromCSV(new DCUFileParser(file), listName, caseType);
    }

    @Caching(evict = {
            @CacheEvict(value = "list", key = "#listName", beforeInvocation = true),
            @CacheEvict(value = "legacyList", key = "#listName", beforeInvocation = true)})
    public DataList createUKVITopicsListFromCSV(MultipartFile file, String listName, String caseType) {
        return createTopicsListFromCSV(new UKVIFileParser(file), listName, caseType);
    }

    public UserCreateRecord getLegacyUsersListByName(String name) throws ListNotFoundException {
        try {
            DataList list = repo.findOneByName(name);
            return UserCreateRecord.create(list);
        } catch (NullPointerException e) {
            throw new ListNotFoundException();
        }
    }

    public UserCreateRecord getLegacyTestUsersListByName(String name) throws ListNotFoundException {
        try {
            DataList list = repo.findOneByName(name);
            return UserCreateRecord.createTest(list);
        } catch (NullPointerException e) {
            throw new ListNotFoundException();
        }
    }

    public UnitRecord getLegacyUnitListByName(String name) throws ListNotFoundException {
        try {
            DataList list = repo.findOneByName(name);
            return UnitRecord.create(list);
        } catch (NullPointerException e) {
            throw new ListNotFoundException();
        }
    }

    public UnitCreateRecord getLegacyUnitCreateListByName(String name) throws ListNotFoundException {
        try {
            DataList list = repo.findOneByName(name);
            return UnitCreateRecord.create(list);
        } catch (NullPointerException e) {
            throw new ListNotFoundException();
        }
    }

    public DataList createTeamsUnitsFromCSV(MultipartFile file, String listName) {
        CSVList list = new UnitFileParser(file);

        Map<String, Set<DataListEntity>> units = new HashMap<>();

        List<CSVUnitLine> lines = list.getLines();
        for (CSVUnitLine line : lines) {
            units.putIfAbsent(line.getUnit(), new HashSet<>());
            units.get(line.getUnit()).add(new DataListEntity(line.getTeam(), line.getTeamValue()));
        }

        Set<DataListEntity> dataListEntities = new HashSet<>();
        for (Map.Entry<String, Set<DataListEntity>> entity : units.entrySet()) {
            DataListEntity dle = new DataListEntity(entity.getKey());
            dle.setSubEntities(entity.getValue());
            dataListEntities.add(dle);
        }

        return new DataList(listName, dataListEntities);
    }

    public DataList createUsersFromCSV(MultipartFile file, String listName) {
        CSVList list = new UserFileParser(file);

        Set<DataListEntity> dataListEntities = new HashSet<>();

        List<CSVUserLine> lines = list.getLines();
        for (CSVUserLine line : lines) {
            Set<DataListEntityProperty> properties = line.getGroups().stream().map(g -> new DataListEntityProperty(g)).collect(Collectors.toSet());

            properties.add(new DataListEntityProperty("firstName", line.getFirst()));
            properties.add(new DataListEntityProperty("lastName", line.getLast()));

            DataListEntity dataListEntity = new DataListEntity(line.getEmail(), false);
            dataListEntity.setProperties(properties);
            dataListEntities.add(dataListEntity);
        }

        return new DataList(listName, dataListEntities);
    }
}
