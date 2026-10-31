"""Create a separate clean Git fixture for native Test1 correction and coverage checks."""
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
LOCAL = HERE / '.local'


def main():
    """Keep existing manual workspaces intact and seed only a new dedicated clone."""
    target = LOCAL / 'workspaces/test1-corrections'
    if target.exists():
        raise RuntimeError('Test1 fixture already exists; preserve its changes and reports')
    subprocess.run(['git', '-c', 'credential.helper=', '-c', 'credential.helper=manager', '-c', 'http.sslBackend=openssl', '-c', 'http.sslCAInfo=' + str(LOCAL / 'tls/ca.crt'), 'clone', 'https://localhost:18443/ods-manual/demo-allowed.git', str(target)], check=True)
    subprocess.run(['git', '-C', str(target), 'switch', '-c', 'test/test1-baseline', 'origin/main'], check=True)
    for key, value in {'http.sslBackend': 'openssl', 'http.sslCAInfo': str(LOCAL / 'tls/ca.crt'), 'user.name': 'user1', 'user.email': 'user1@manual.test', 'credential.helper': 'manager'}.items():
        subprocess.run(['git', '-C', str(target), 'config', key, value], check=True)
    shutil.copytree(HERE / 'demo', target, dirs_exist_ok=True)
    pom = (target / 'pom.xml').read_text(encoding='utf8')
    pom = pom.replace('<dependencies>', '<dependencies>\n    <dependency><groupId>org.apache.commons</groupId><artifactId>commons-lang3</artifactId><version>3.17.0</version></dependency>')
    pom = pom.replace('<build><plugins>', '<build><plugins>\n    <plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId><version>0.8.14</version><executions><execution><goals><goal>prepare-agent</goal></goals></execution><execution><id>report</id><phase>verify</phase><goals><goal>report</goal></goals></execution></executions></plugin>')
    ET.fromstring(pom)
    (target / 'pom.xml').write_text(pom, encoding='utf8')
    (target / 'src/main/java/local/manual/Calculator.java').write_text('''package local.manual;

/** Disposable arithmetic fixture. Exigences : DEVAI-090. */
public class Calculator {
    /** Adds two values. Exigences : DEVAI-090. */
    public int add(int left, int right) {
        System.out.println("Manual test calculation");
        return left + right;
    }
    /** Subtracts the second operand. Exigences : DEVAI-090. */
    public int subtract(int left, int right) { return left - right; }
    /** Multiplies operands. Exigences : DEVAI-090. */
    public int multiply(int left, int right) { return left * right; }
    /** Distinguishes negative, zero and positive inputs. Exigences : DEVAI-090. */
    public int sign(int value) {
        if (value < 0) return -1;
        if (value == 0) return 0;
        return 1;
    }
}
''', encoding='utf8')
    (target / '.gitignore').write_text('target/\n.scannerwork/\n.ods-local/\n.mvn/\n', encoding='utf8')
    (target / 'AGENTS.md').write_text('Test fixture. Preserve all arithmetic behavior and existing tests. Use the existing Maven/JUnit/JaCoCo configuration. For coverage add test cases without editing production code or excluding measured sources. Document every changed Java class and method with a short comment and DEVAI-090. The dependency alert XRAY-MOCK-0001 is synthetic; upgrade commons-lang3 to its reported fixed version.\n', encoding='utf8')
    mvn = target / '.mvn'
    mvn.mkdir(exist_ok=True)
    (mvn / 'jvm.config').write_text('-Djavax.net.ssl.trustStore=../../jdk/lib/security/cacerts\n-Djavax.net.ssl.trustStorePassword=changeit\n')
    (mvn / 'maven.config').write_text('-Dsonar.scanner.skipJreProvisioning=true\n"-Dsonar.scanner.javaExePath=' + str(LOCAL / 'jdk/bin/java.exe') + '"\n')
    subprocess.run(['git', '-C', str(target), 'add', 'pom.xml', 'src', '.gitignore', 'AGENTS.md'], check=True)
    subprocess.run(['git', '-C', str(target), 'commit', '-m', 'test: isolated correction and coverage baseline'], check=True)
    print('Test1 fixture created; existing manual workspaces preserved.')


if __name__ == '__main__':
    main()
