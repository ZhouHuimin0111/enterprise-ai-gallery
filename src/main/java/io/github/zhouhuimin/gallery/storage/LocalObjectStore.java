package io.github.zhouhuimin.gallery.storage;
import io.github.zhouhuimin.gallery.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component @ConditionalOnProperty(name="app.storage.mode",havingValue="local",matchIfMissing=true)
public class LocalObjectStore implements ObjectStore {
    private final Path root;
    public LocalObjectStore(@Value("${app.storage.local-path}") String path) { root=Path.of(path).toAbsolutePath().normalize();try { Files.createDirectories(root); } catch(Exception e) { throw new IllegalStateException("Local object directory unavailable"); } }
    private Path path(String key) { BusinessException.require(key.matches("objects/[a-f0-9-]{36}\\.png"),ErrorCode.BAD_REQUEST);var p=root.resolve(key).normalize();BusinessException.require(p.startsWith(root),ErrorCode.BAD_REQUEST);return p; }
    public void put(String key,byte[] bytes,String type) {
        Path target=path(key),temp=null;
        try { Files.createDirectories(target.getParent());temp=Files.createTempFile(target.getParent(),"upload-",".tmp");Files.write(temp,bytes);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE); }
        catch(Exception e) { throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE); }
        finally { if(temp!=null) try { Files.deleteIfExists(temp); } catch(Exception ignored) {} }
    }
    public byte[] read(String key) { try { return Files.readAllBytes(path(key)); } catch(Exception e) { throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE); } }
    public void delete(String key) { try { Files.deleteIfExists(path(key)); } catch(Exception e) { throw new BusinessException(ErrorCode.STORAGE_UNAVAILABLE); } }
    public String mode() { return "local"; }
}
