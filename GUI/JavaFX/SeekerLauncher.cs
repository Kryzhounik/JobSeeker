using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class SeekerLauncher
{
    [STAThread]
    private static int Main()
    {
        string directory = AppDomain.CurrentDomain.BaseDirectory;
        string classes = Path.Combine(directory, "target", "classes");
        string dependencies = Path.Combine(directory, "target", "dependency");
        string root = Path.GetFullPath(Path.Combine(directory, "..", ".."));
        string log = Path.Combine(directory, "launch.log");

        try
        {
            if (!File.Exists(Path.Combine(classes, "com", "seeker", "guifx", "SeekerLauncher.class"))
                    || !Directory.Exists(dependencies))
            {
                throw new FileNotFoundException("JavaFX build is missing. Run build_launcher.ps1 first.");
            }

            string javaHomeFile = Path.Combine(directory, "java-home.txt");
            string javaHome = File.Exists(javaHomeFile)
                    ? File.ReadAllText(javaHomeFile).Trim()
                    : Environment.GetEnvironmentVariable("JAVA_HOME");
            string javaw = Path.Combine(javaHome ?? "", "bin", "javaw.exe");
            if (!File.Exists(javaw))
            {
                throw new FileNotFoundException("Java runtime not found: " + javaw
                        + ". Run build_launcher.ps1 with a valid JDK 21 path.");
            }

            ProcessStartInfo start = new ProcessStartInfo();
            start.FileName = javaw;
            start.Arguments = "-cp " + Quote(classes + ";" + Path.Combine(dependencies, "*"))
                    + " com.seeker.guifx.SeekerLauncher";
            start.WorkingDirectory = root;
            start.UseShellExecute = false;
            start.CreateNoWindow = true;
            start.RedirectStandardOutput = true;
            start.RedirectStandardError = true;

            using (StreamWriter writer = new StreamWriter(log, true))
            using (Process process = new Process())
            {
                object logLock = new object();
                writer.AutoFlush = true;
                writer.WriteLine("--- " + DateTime.Now.ToString("u") + " ---");
                process.StartInfo = start;
                process.OutputDataReceived += (sender, eventArgs) => {
                    if (eventArgs.Data != null) lock (logLock) writer.WriteLine(eventArgs.Data);
                };
                process.ErrorDataReceived += (sender, eventArgs) => {
                    if (eventArgs.Data != null) lock (logLock) writer.WriteLine(eventArgs.Data);
                };
                process.Start();
                process.BeginOutputReadLine();
                process.BeginErrorReadLine();
                process.WaitForExit();
                if (process.ExitCode != 0)
                {
                    MessageBox.Show("JavaFX exited with code " + process.ExitCode
                            + ". See " + log, "Seeker Jobs", MessageBoxButtons.OK,
                            MessageBoxIcon.Error);
                }
                return process.ExitCode;
            }
        }
        catch (Exception error)
        {
            MessageBox.Show(error.Message, "Seeker Jobs", MessageBoxButtons.OK,
                    MessageBoxIcon.Error);
            return 1;
        }
    }

    private static string Quote(string value)
    {
        return "\"" + value.Replace("\"", "\\\"") + "\"";
    }
}
