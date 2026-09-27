Add-Type -AssemblyName System.Drawing;
$url = 'https://img.itch.zone/aW1nLzkyMjI0NzYucG5n/original/CkuiDh.png';
$tempPath = 'temp_icon.png';
Invoke-WebRequest -Uri $url -OutFile $tempPath;

$img = [System.Drawing.Image]::FromFile($tempPath);
$resDir = 'app/src/main/res';

$sizes = @{
    'mipmap-mdpi' = 48;
    'mipmap-hdpi' = 72;
    'mipmap-xhdpi' = 96;
    'mipmap-xxhdpi' = 144;
    'mipmap-xxxhdpi' = 192
};

foreach ($folder in $sizes.Keys) {
    $size = $sizes[$folder];
    $folderPath = Join-Path $resDir $folder;
    if (-not (Test-Path $folderPath)) {
        New-Item -ItemType Directory -Path $folderPath | Out-Null;
    }
    
    $bmp = New-Object System.Drawing.Bitmap($size, $size);
    $g = [System.Drawing.Graphics]::FromImage($bmp);
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic;
    $g.DrawImage($img, 0, 0, $size, $size);
    $g.Dispose();
    
    $bmp.Save((Join-Path $folderPath 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png);
    $bmp.Save((Join-Path $folderPath 'ic_launcher_round.png'), [System.Drawing.Imaging.ImageFormat]::Png);
    $bmp.Dispose();
}

$bannerFolder = Join-Path $resDir 'mipmap-xhdpi';
if (-not (Test-Path $bannerFolder)) {
    New-Item -ItemType Directory -Path $bannerFolder | Out-Null;
}
$bannerBmp = New-Object System.Drawing.Bitmap(320, 180);
$g2 = [System.Drawing.Graphics]::FromImage($bannerBmp);
$g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic;
$g2.DrawImage($img, 0, 0, 320, 180);
$g2.Dispose();
$bannerBmp.Save((Join-Path $bannerFolder 'ic_banner.png'), [System.Drawing.Imaging.ImageFormat]::Png);
$bannerBmp.Dispose();

$img.Dispose();
Remove-Item $tempPath;
Write-Host 'Icons replaced successfully.';
