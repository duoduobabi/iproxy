package org.cuiyang.iproxy.mitm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Authority {
    @Builder.Default
    private final File keyStoreDir = new File(".");
    @Builder.Default
    private final String alias = "IProxy";
    @Builder.Default
    private final char[] password = "123456".toCharArray();
    @Builder.Default
    private final String commonName = "IProxy CA";
    @Builder.Default
    private final String organization = "IProxy";
    @Builder.Default
    private final String organizationalUnitName = "IProxy CA";
    @Builder.Default
    private final String certOrganization = "IProxy";
    @Builder.Default
    private final String certOrganizationalUnitName = "IProxy CA";

    public File aliasFile(String fileExtension) {
        return new File(keyStoreDir, alias + fileExtension);
    }
}
