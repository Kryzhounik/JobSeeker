using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class JobsViewerLauncher
{
    [STAThread]
    private static int Main()
    {
        string baseDirectory = AppDomain.CurrentDomain.BaseDirectory;
        string viewerPath = Path.Combine(baseDirectory, "jobs_viewer.py");
        string pythonwPath = @"C:\Users\user\AppData\Local\Python\pythoncore-3.14-64\pythonw.exe";

        if (!File.Exists(viewerPath))
        {
            MessageBox.Show(
                "Cannot find jobs_viewer.py next to JobsViewer.exe.",
                "Seeker Jobs Viewer",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error
            );
            return 1;
        }

        if (!File.Exists(pythonwPath))
        {
            pythonwPath = "pythonw.exe";
        }

        try
        {
            ProcessStartInfo startInfo = new ProcessStartInfo();
            startInfo.FileName = pythonwPath;
            startInfo.Arguments = Quote(viewerPath);
            startInfo.WorkingDirectory = baseDirectory;
            startInfo.UseShellExecute = false;
            Process.Start(startInfo);
            return 0;
        }
        catch (Exception error)
        {
            MessageBox.Show(
                error.Message,
                "Seeker Jobs Viewer",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error
            );
            return 1;
        }
    }

    private static string Quote(string value)
    {
        return "\"" + value.Replace("\"", "\\\"") + "\"";
    }
}
