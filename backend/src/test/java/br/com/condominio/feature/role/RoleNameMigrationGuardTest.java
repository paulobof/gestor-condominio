package br.com.condominio.feature.role;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards against a migration inserting a role whose name is missing from {@link RoleName}. */
class RoleNameMigrationGuardTest {

  private static final Pattern INSERT =
      Pattern.compile("INSERT INTO role [(][^)]*[)].{0,5}?VALUES(.*?);", Pattern.DOTALL);
  private static final Pattern ROW_NAME = Pattern.compile("[(] *[0-9]+ *, *'([A-Z_]+)'");
  private static final Pattern DELETE =
      Pattern.compile("DELETE FROM role WHERE name IN [(]([^)]*)[)]");
  private static final Pattern QUOTED = Pattern.compile("'([A-Z_]+)'");

  @Test
  void everyRoleNameInsertedByMigrationsExistsInEnum() throws Exception {
    Set<String> inserted = new TreeSet<>();
    Set<String> deleted = new TreeSet<>();
    try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
      for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
        String sql = Files.readString(p);
        Matcher ins = INSERT.matcher(sql);
        while (ins.find()) {
          Matcher row = ROW_NAME.matcher(ins.group(1));
          while (row.find()) {
            inserted.add(row.group(1));
          }
        }
        Matcher del = DELETE.matcher(sql);
        while (del.find()) {
          Matcher q = QUOTED.matcher(del.group(1));
          while (q.find()) {
            deleted.add(q.group(1));
          }
        }
      }
    }
    inserted.removeAll(deleted);
    Set<String> enumNames = new TreeSet<>();
    Arrays.stream(RoleName.values()).forEach(r -> enumNames.add(r.name()));
    assertThat(inserted).contains("COMPLAINT_MANAGER", "MANAGER").isSubsetOf(enumNames);
  }
}
