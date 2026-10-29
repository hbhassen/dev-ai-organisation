package org.organisation.ods.runtime;

import org.organisation.ods.contracts.OdsException;
import java.nio.file.*;
import java.net.URI;
import java.util.*;

/** Clones a user-selected remote into a new destination without changing any existing checkout. Exigences : DEVAI-023, DEVAI-107. */
public final class GitClone {
    /** Prevents mutable instances of the native clone boundary. Exigences : DEVAI-107. */
    private GitClone() { }
    /** Validates structured remote and destination values before invoking Git with the user's identity. Exigences : DEVAI-107, DEVAI-155. */
    public static Map<String,Object> clone(ProcessRunner runner,Path parent,String name,String remote) throws Exception {
        if(!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{1,60}")||name.endsWith(".")) throw new OdsException("INVALID_REQUEST","Nom de dossier invalide");
        if(remote.startsWith("https://")) { URI uri=URI.create(remote); if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null) throw new OdsException("INVALID_REQUEST","URL HTTPS sans credentials requise"); }
        else if(!remote.matches("git@[a-zA-Z0-9.-]+:[a-zA-Z0-9_.-]+(?:/[a-zA-Z0-9_.-]+)+")) throw new OdsException("INVALID_REQUEST","Remote HTTPS ou SSH Git requis");
        Path base=parent.toRealPath(), destination=base.resolve(name);
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) throw new OdsException("DESTINATION_EXISTS","Destination existante ; aucun fichier modifié");
        var result=runner.run(base,List.of("git","clone","--",remote,name),600);
        if(!Objects.equals(result.get("exitCode"),0)) throw new OdsException("GIT_FAILED","Clone échoué ; consulter le dossier partiel avant de réessayer : "+result.get("output"));
        return Map.of("created",name,"git",result);
    }
}
