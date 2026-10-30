// MXO QoL Patch installer: borderless window, DXVK, mxoperf, hardware vertex processing and the
// alt-tab music fix for The Matrix Online 7.6005.
// Build: build.cmd (uses the .NET Framework 4 csc.exe that ships with Windows).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("MXO QoL Patch Installer")]
[assembly: AssemblyDescription("Installs and uninstalls the MXO QoL Patch for The Matrix Online 7.6005")]
[assembly: AssemblyCompany("cgtheodorou")]
[assembly: AssemblyProduct("MXO QoL Patch")]
[assembly: AssemblyCopyright("cgtheodorou")]
[assembly: AssemblyVersion("1.1.1.0")]
[assembly: AssemblyFileVersion("1.1.1.0")]
[assembly: AssemblyInformationalVersion("1.1.1")]

static class Program
{
    public const string Version = "1.1.1";

    [STAThread]
    static int Main(string[] args)
    {
        // Command line: /install "<MxO folder>" [/nodxvk] [/noborderless] [/noperf] [/nomusic]   |   /uninstall "<MxO folder>"
        if (args.Length >= 2 && (Is(args[0], "/install") || Is(args[0], "/uninstall")))
        {
            Native.AttachConsole(-1);
            var opt = new Options { Dxvk = true, Borderless = true, Perf = true, Music = true };
            for (int i = 2; i < args.Length; i++)
            {
                if (Is(args[i], "/nodxvk")) opt.Dxvk = false;
                if (Is(args[i], "/noborderless")) opt.Borderless = false;
                if (Is(args[i], "/noperf")) opt.Perf = false;
                if (Is(args[i], "/nomusic")) opt.Music = false;
            }
            var p = new Patcher(args[1], Console.WriteLine);
            bool ok = Is(args[0], "/install") ? p.Install(opt) : p.Uninstall();
            return ok ? 0 : 1;
        }
        Application.EnableVisualStyles();
        // A single folder argument (used when restarting as administrator) pre-fills the folder box.
        Application.Run(new MainForm(args.Length == 1 ? args[0] : null));
        return 0;
    }

    static bool Is(string a, string b) { return string.Equals(a, b, StringComparison.OrdinalIgnoreCase); }
}

class Options { public bool Dxvk, Borderless, Perf, Music; }

class Patcher
{
    const string BlockStart = "# --- MXO QoL patch (managed, removed on uninstall) ---";
    const string BlockEnd = "# --- end MXO QoL patch ---";

    readonly string game, backup, client, cfg, state;
    readonly Action<string> log;

    public Patcher(string gameDir, Action<string> log)
    {
        game = gameDir.TrimEnd('\\', '/');
        backup = Path.Combine(game, "QoL_backup");
        client = Path.Combine(game, "client.dll");
        cfg = Path.Combine(game, "autoexec.cfg");
        state = Path.Combine(backup, "installed_files.txt");
        this.log = log;
    }

    public bool IsInstalled { get { return File.Exists(state); } }

    public static bool LooksLikeGame(string dir)
    {
        return File.Exists(Path.Combine(dir, "client.dll")) && File.Exists(Path.Combine(dir, "r3d9.dll"));
    }

    // ------------------------------------------------------------------ install
    public bool Install(Options opt)
    {
        try
        {
            log("Installing to " + game);
            if (!Preflight()) return false;
            foreach (var f in DataFiles)
                if (!File.Exists(Path.Combine(DataDir, f)))
                    return Fail("data\\" + f + " is missing. Extract the whole zip and run the installer from the extracted folder.");
            Directory.CreateDirectory(backup);

            if (!PatchClient()) return false;
            if (!PatchMusic(opt.Music)) return false;

            var files = new List<string>();
            // r3dx.dll: the player's own r3d9.dll, importing dxv9.dll (DXVK) instead of d3d9.dll.
            byte[] r3d9 = File.ReadAllBytes(Path.Combine(game, "r3d9.dll"));
            string dxv9 = Path.Combine(game, "dxv9.dll");
            if (opt.Dxvk)
            {
                int at = FindOnce(r3d9, Encoding.ASCII.GetBytes("d3d9.dll"));
                if (at < 0) return Fail("r3d9.dll is not the expected build (d3d9.dll import not found).");
                Encoding.ASCII.GetBytes("dxv9.dll").CopyTo(r3d9, at);
                File.WriteAllBytes(dxv9, Resource("dxv9.dll"));
                File.WriteAllBytes(Path.Combine(game, "dxvk.conf"), Resource("dxvk.conf"));
                File.WriteAllBytes(Path.Combine(game, "DXVK_LICENSE.txt"), Resource("DXVK_LICENSE.txt"));
                files.AddRange(new[] { "dxv9.dll", "dxvk.conf", "DXVK_LICENSE.txt", "matrix_d3d9.log" });
                log("  DXVK on (Vulkan renderer).");
            }
            else
            {
                foreach (var f in new[] { "dxv9.dll", "dxvk.conf", "DXVK_LICENSE.txt" }) Delete(Path.Combine(game, f));
                log("  DXVK off (stock Direct3D 9).");
            }
            File.WriteAllBytes(Path.Combine(game, "r3dx.dll"), r3d9);
            files.Add("r3dx.dll");

            string perf = Path.Combine(game, "mxoperf.dll");
            if (opt.Perf)
            {
                File.WriteAllBytes(perf, Resource("mxoperf.dll"));
                string ini = Path.Combine(game, "mxoperf.ini");
                if (!File.Exists(ini)) File.WriteAllBytes(ini, Resource("mxoperf.ini"));
                files.AddRange(new[] { "mxoperf.dll", "mxoperf.ini", "mxoperf_log.txt" });
                log("  mxoperf on (mxoperf.ini has the settings; Ctrl+Alt+O toggles the FPS overlay).");
            }
            else
            {
                Delete(perf);
                Delete(Path.Combine(game, "mxoperf.ini"));
                log("  mxoperf off.");
            }
            File.WriteAllLines(state, files.ToArray());

            RemoveCfgBlock();
            if (opt.Borderless)
            {
                int w = Native.GetSystemMetrics(0), h = Native.GetSystemMetrics(1);
                File.AppendAllText(cfg, "\r\n" + BlockStart + "\r\nWindowed = 2\r\nScreenWidth = " + w +
                    "\r\nScreenHeight = " + h + "\r\n" + BlockEnd + "\r\n");
                log("  Borderless on: autoexec.cfg -> Windowed = 2 at " + w + "x" + h + ".");
                log("  Keep the Rejacked launcher's window mode on \"Game setting\" or it overrides this.");
            }
            else log("  Borderless off (window mode left alone).");

            log("Done. Start the game.");
            return true;
        }
        catch (Exception e) { return Fail(e.Message + AdminHint(e)); }
    }

    bool PatchClient()
    {
        var sites = ReadPatch();
        byte[] buf = File.ReadAllBytes(client);
        int asOld = 0, asNew = 0;
        foreach (var s in sites)
        {
            if (Same(buf, s.Off, s.Old)) asOld++;
            if (Same(buf, s.Off, s.New)) asNew++;
        }
        if (asNew == sites.Count) { log("  client.dll already patched."); return true; }
        if (asOld != sites.Count)
            return Fail("client.dll is not the 7.6005 build this patch was made for (" + asOld + " of " + sites.Count +
                        " blocks match). Nothing was changed.");
        string bak = Path.Combine(backup, "client.dll");
        if (!File.Exists(bak)) { File.Copy(client, bak); log("  Backed up client.dll."); }
        foreach (var s in sites) Buffer.BlockCopy(s.New, 0, buf, s.Off, s.New.Length);
        File.WriteAllBytes(client, buf);
        log("  client.dll patched (borderless, DXVK loader, mxoperf hook, hardware vertex processing).");
        return true;
    }

    // Alt-tab music fix: restoring the game from a minimised fullscreen window runs the music "pause"
    // twice, and the second run saves the already-zero volume over the real one. The fixed pause only
    // saves the volume when it is not zero. Found by pattern (the pause plus the resume that follows it).
    const string MusicOriginal = "558BEC568BF18A461584C074138B06FF5044D95E648B45088B16508BCEFF52485E5DC20400CCCCCCCCCCCCCCCCCCCCCC";
    const string MusicFixed = "558BEC568BF1807E150074158B460485C074038946648B45088B16508BCEFF52485E5DC20400CCCCCCCCCCCCCCCCCCCC";
    const string MusicResume = "8A411584C074098B51648B0152FF5048C3";

    bool PatchMusic(bool on)
    {
        byte[] orig = Hex(MusicOriginal), fix = Hex(MusicFixed);
        byte[] tail = Hex(MusicResume);
        byte[] buf = File.ReadAllBytes(client);
        int atOrig = FindOnce(buf, Concat(orig, tail)), atFix = FindOnce(buf, Concat(fix, tail));
        if (on)
        {
            if (atFix >= 0) { log("  Music fix already in client.dll."); return true; }
            if (atOrig < 0) return Fail("Couldn't find the music code in client.dll (unknown build). Untick the music fix and try again.");
            Buffer.BlockCopy(fix, 0, buf, atOrig, fix.Length);
            File.WriteAllBytes(client, buf);
            log("  Music fix on (music no longer goes silent after alt-tab).");
        }
        else if (atFix >= 0)
        {
            Buffer.BlockCopy(orig, 0, buf, atFix, orig.Length);
            File.WriteAllBytes(client, buf);
            log("  Music fix off.");
        }
        else log("  Music fix off.");
        return true;
    }

    static byte[] Concat(byte[] a, byte[] b)
    {
        var r = new byte[a.Length + b.Length];
        Buffer.BlockCopy(a, 0, r, 0, a.Length);
        Buffer.BlockCopy(b, 0, r, a.Length, b.Length);
        return r;
    }

    // ------------------------------------------------------------------ uninstall
    public bool Uninstall()
    {
        try
        {
            log("Uninstalling from " + game);
            if (!Preflight()) return false;
            if (!Directory.Exists(backup)) return Fail("Nothing to uninstall (no QoL_backup folder).");
            string bak = Path.Combine(backup, "client.dll");
            if (File.Exists(bak)) { File.Copy(bak, client, true); log("  Restored client.dll."); }
            if (File.Exists(state))
                foreach (var f in File.ReadAllLines(state))
                    if (f.Trim().Length > 0 && Delete(Path.Combine(game, f))) log("  Removed " + f);
            RemoveCfgBlock();
            log("  Removed the borderless lines from autoexec.cfg.");
            Directory.Delete(backup, true);
            log("Done. The game is back to stock.");
            return true;
        }
        catch (Exception e) { return Fail(e.Message + AdminHint(e)); }
    }

    // ------------------------------------------------------------------ helpers
    bool Preflight()
    {
        if (!File.Exists(client) || !File.Exists(Path.Combine(game, "r3d9.dll")))
            return Fail("That folder has no client.dll / r3d9.dll. Pick your Matrix Online folder.");
        if (Process.GetProcessesByName("matrix").Length > 0) return Fail("The game is running. Close it first.");
        return true;
    }

    void RemoveCfgBlock()
    {
        if (!File.Exists(cfg)) return;
        string text = File.ReadAllText(cfg);
        string re = "(\r\n)?" + Regex.Escape(BlockStart) + "[\\s\\S]*?" + Regex.Escape(BlockEnd) + "\r\n";
        string stripped = Regex.Replace(text, re, "");
        if (stripped != text) File.WriteAllText(cfg, stripped);
    }

    bool Fail(string msg) { log("! " + msg); return false; }

    // Set when Windows refused a write, so the window can offer to restart the installer as administrator.
    public bool NeedsAdmin;

    string AdminHint(Exception e)
    {
        if (!(e is UnauthorizedAccessException)) return "";
        NeedsAdmin = true;
        return " (the game folder needs administrator rights)";
    }

    static bool Delete(string path)
    {
        if (!File.Exists(path)) return false;
        File.Delete(path);
        return true;
    }

    static bool Same(byte[] buf, int off, byte[] want)
    {
        if (off + want.Length > buf.Length) return false;
        for (int i = 0; i < want.Length; i++) if (buf[off + i] != want[i]) return false;
        return true;
    }

    static int FindOnce(byte[] hay, byte[] needle)
    {
        int found = -1;
        for (int i = 0; i + needle.Length <= hay.Length; i++)
        {
            if (!Same(hay, i, needle)) continue;
            if (found >= 0) return -1;
            found = i;
        }
        return found;
    }

    class Site { public int Off; public byte[] Old, New; }

    static List<Site> ReadPatch()
    {
        var list = new List<Site>();
        foreach (var line in Encoding.ASCII.GetString(Resource("client_qol.patch")).Split('\n'))
        {
            var f = line.Trim().Split(' ');
            if (f.Length != 3) continue;
            list.Add(new Site { Off = Convert.ToInt32(f[0], 16), Old = Hex(f[1]), New = Hex(f[2]) });
        }
        return list;
    }

    static byte[] Hex(string s)
    {
        var b = new byte[s.Length / 2];
        for (int i = 0; i < b.Length; i++) b[i] = Convert.ToByte(s.Substring(i * 2, 2), 16);
        return b;
    }

    // The files the installer copies live in a "data" folder next to the exe (shipped in the same zip).
    static readonly string DataDir = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "data");
    static readonly string[] DataFiles = { "client_qol.patch", "dxv9.dll", "dxvk.conf", "DXVK_LICENSE.txt", "mxoperf.dll", "mxoperf.ini" };

    static byte[] Resource(string name)
    {
        return File.ReadAllBytes(Path.Combine(DataDir, name));
    }
}

static class Native
{
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    [DllImport("kernel32.dll")] public static extern bool AttachConsole(int processId);
}

class MainForm : Form
{
    readonly TextBox folder = new TextBox();
    readonly CheckBox dxvk = new CheckBox(), borderless = new CheckBox(), perf = new CheckBox(), music = new CheckBox();
    readonly Button install = new Button(), uninstall = new Button();
    readonly TextBox logBox = new TextBox();
    readonly Label status = new Label();

    public MainForm(string startFolder)
    {
        SuspendLayout();
        Font = new Font("Segoe UI", 9f);
        AutoScaleDimensions = new SizeF(7f, 15f);
        AutoScaleMode = AutoScaleMode.Font;
        Text = "MXO QoL Patch " + Program.Version;
        ClientSize = new Size(560, 466);
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }

        var intro = new Label
        {
            Text = "Close the game before installing.",
            Location = new Point(12, 14), AutoSize = true
        };
        var folderLabel = new Label { Text = "Matrix Online folder (has client.dll):", Location = new Point(12, 58), AutoSize = true };
        folder.Location = new Point(12, 80); folder.Width = 440;
        folder.TextChanged += delegate { Refresh(); };
        var browse = new Button { Text = "Browse...", Location = new Point(460, 78), Width = 88 };
        browse.Click += delegate
        {
            using (var d = new FolderBrowserDialog { Description = "Select your Matrix Online folder (contains client.dll)" })
            {
                if (Directory.Exists(folder.Text)) d.SelectedPath = folder.Text;
                if (d.ShowDialog(this) == DialogResult.OK) folder.Text = d.SelectedPath;
            }
        };

        dxvk.Text = "DXVK - Vulkan renderer (untick if the game won't start)";
        borderless.Text = "Borderless window at your desktop resolution";
        perf.Text = "mxoperf - FPS cap, FPS overlay, fast-core pinning (mxoperf.ini)";
        music.Text = "Fix music going silent after alt-tab";
        int y = 116;
        foreach (var c in new[] { dxvk, borderless, perf, music })
        {
            c.Checked = true; c.AutoSize = true; c.Location = new Point(12, y); y += 26;
        }

        install.Text = "Install"; install.Location = new Point(12, 226); install.Size = new Size(110, 30);
        uninstall.Text = "Uninstall"; uninstall.Location = new Point(130, 226); uninstall.Size = new Size(110, 30);
        install.Click += delegate { Run(true); };
        uninstall.Click += delegate { Run(false); };
        status.Location = new Point(252, 233); status.AutoSize = true;

        logBox.Multiline = true; logBox.ReadOnly = true; logBox.ScrollBars = ScrollBars.Vertical;
        logBox.Location = new Point(12, 266); logBox.Size = new Size(536, 188);
        logBox.BackColor = SystemColors.Window;

        Controls.AddRange(new Control[] { intro, folderLabel, folder, browse, dxvk, borderless, perf, music, install, uninstall, status, logBox });
        ResumeLayout(false);
        PerformLayout();
        folder.Text = startFolder != null && Patcher.LooksLikeGame(startFolder) ? startFolder : DetectFolder();
        Refresh();
    }

    new void Refresh()
    {
        bool ok = Patcher.LooksLikeGame(folder.Text);
        bool inst = ok && new Patcher(folder.Text, delegate { }).IsInstalled;
        install.Enabled = ok;
        install.Text = inst ? "Reinstall" : "Install";
        uninstall.Enabled = inst;
        status.Text = !ok ? "No client.dll / r3d9.dll in this folder." : inst ? "Installed." : "Not installed.";
    }

    void Run(bool doInstall)
    {
        logBox.Clear();
        var p = new Patcher(folder.Text, delegate(string s) { logBox.AppendText(s + Environment.NewLine); });
        Cursor = Cursors.WaitCursor;
        bool ok = doInstall
            ? p.Install(new Options { Dxvk = dxvk.Checked, Borderless = borderless.Checked, Perf = perf.Checked, Music = music.Checked })
            : p.Uninstall();
        Cursor = Cursors.Default;
        Refresh();
        if (!ok && p.NeedsAdmin && MessageBox.Show(this,
                "Windows needs administrator rights to change files in this folder. Restart the installer as administrator?",
                Text, MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
        {
            try
            {
                Process.Start(new ProcessStartInfo(Application.ExecutablePath, "\"" + folder.Text + "\"") { Verb = "runas", UseShellExecute = true });
                Close();
            }
            catch (System.ComponentModel.Win32Exception) { } // the user said no to the Windows prompt
        }
    }

    static string DetectFolder()
    {
        var candidates = new List<string>();
        try
        {
            using (var k = Registry.CurrentUser.OpenSubKey(@"Software\Rejacked\Rejacked Launcher\game"))
                if (k != null && k.GetValue("install_dir") != null) candidates.Add(k.GetValue("install_dir").ToString().Replace('/', '\\'));
        }
        catch { }
        candidates.AddRange(new[]
        {
            @"C:\Games\MxO_7.6005",
            @"C:\Program Files (x86)\Sony Online Entertainment\The Matrix Online",
            @"C:\Program Files\Sony Online Entertainment\The Matrix Online",
            @"C:\Program Files (x86)\The Matrix Online",
            @"C:\Program Files\The Matrix Online",
            @"C:\The Matrix Online",
        });
        foreach (var c in candidates) if (Patcher.LooksLikeGame(c)) return c;
        return "";
    }
}
