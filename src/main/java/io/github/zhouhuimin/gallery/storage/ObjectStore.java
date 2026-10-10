package io.github.zhouhuimin.gallery.storage;
public interface ObjectStore {
    void put(String key,byte[] bytes,String contentType);
    byte[] read(String key);
    void delete(String key);
    String mode();
}
