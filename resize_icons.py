from PIL import Image
import os

# 源图标路径
source_icon = r"c:\Users\Quick\AndroidStudioProjects\Aurora\Screenshot 2026-03-06.webp"

# Android 标准图标尺寸
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

# 打开源图标
img = Image.open(source_icon)

# 为每个密度创建图标
for density, size in sizes.items():
    # 调整大小
    resized = img.resize((size, size), Image.Resampling.LANCZOS)

    # 保存到对应的 mipmap 文件夹
    output_path = rf"c:\Users\Quick\AndroidStudioProjects\Aurora\app\src\main\res\mipmap-{density}\ic_launcher.webp"
    resized.save(output_path, 'WEBP', quality=95)
    print(f"Created {density}: {size}x{size} -> {output_path}")

    # 同时创建 round 版本
    output_path_round = rf"c:\Users\Quick\AndroidStudioProjects\Aurora\app\src\main\res\mipmap-{density}\ic_launcher_round.webp"
    resized.save(output_path_round, 'WEBP', quality=95)
    print(f"Created {density} round: {size}x{size} -> {output_path_round}")

# 为 drawable 创建一个中等尺寸的版本
drawable_size = 96  # xhdpi 尺寸
drawable_img = img.resize((drawable_size, drawable_size), Image.Resampling.LANCZOS)
drawable_path = r"c:\Users\Quick\AndroidStudioProjects\Aurora\app\src\main\res\drawable\aurora_icon.webp"
drawable_img.save(drawable_path, 'WEBP', quality=95)
print(f"Created drawable icon: {drawable_size}x{drawable_size} -> {drawable_path}")

print("\n所有图标已成功创建！")
