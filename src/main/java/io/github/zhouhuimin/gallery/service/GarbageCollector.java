package io.github.zhouhuimin.gallery.service;
import io.github.zhouhuimin.gallery.mapper.MaintenanceMapper;
import io.github.zhouhuimin.gallery.storage.ObjectStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @Slf4j
public class GarbageCollector {
    private final MaintenanceMapper maintenance;private final ObjectStore storage;
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void collect() {
        for(String key:maintenance.garbage()) {
            try { if(maintenance.references(key)==0) storage.delete(key);maintenance.gcDone(key); }
            catch(Exception e) { maintenance.gcFailed(key);log.warn("Object cleanup deferred"); }
        }
    }
}
