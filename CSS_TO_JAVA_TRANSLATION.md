# CSS到Java的翻译对照表

## 1. 顶栏样式 (top-header)

### CSS:
```css
.top-header {
  height: 54px;
  background-color: #f9f9f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #e5e5e5;
}
```

### Java翻译:
```java
// 顶栏背景
float headerHeight = 54f;
drawQuad(g, 0, 0, windowWidth, headerHeight, new Color4b(249, 249, 249, 255));
// 底部边框
drawQuad(g, 0, headerHeight, windowWidth, headerHeight + 1, new Color4b(229, 229, 229, 255));
```

## 2. Logo图标 (logo-icon)

### CSS:
```css
.logo-icon { 
  width: 24px; 
  height: 24px; 
  background: #ec4141; 
  color: #fff; 
  border-radius: 50%; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  font-size: 11px; 
}
```

### Java翻译:
```java
// 圆形Logo背景
float logoX = 16 + 12; // padding + 半径
float logoY = 27; // 居中位置
drawRounded(g, logoX - 12, logoY - 12, logoX + 12, logoY + 12, 
    12, new Color4b(236, 65, 65, 255), Color4b.TRANSPARENT);
// 内部图标 (需要用FontAwesome或自绘)
```

## 3. 搜索框 (search-bar)

### CSS:
```css
.search-bar { 
  background: #ededed; 
  border-radius: 15px; 
  padding: 4px 12px; 
  display: flex; 
  align-items: center; 
  gap: 8px; 
  font-size: 12px; 
  color: #888; 
}
```

### Java翻译:
```java
float searchX = logoX + 180;
float searchY = 7;
float searchWidth = 220;
float searchHeight = 30;
drawRounded(g, searchX, searchY, searchX + searchWidth, searchY + searchHeight,
    15, new Color4b(237, 237, 237, 255), Color4b.TRANSPARENT);
```

## 4. 侧边栏菜单项 (menu-item)

### CSS:
```css
.menu-item { 
  display: flex; 
  align-items: center; 
  gap: 10px; 
  padding: 8px 12px; 
  border-radius: 6px; 
  font-size: 13px; 
  color: #4a4a4a; 
  cursor: pointer; 
  margin-bottom: 2px; 
}
.menu-item.active { 
  background-color: #ec4141; 
  color: #fff; 
  font-weight: bold; 
}
```

### Java翻译:
```java
float itemHeight = 40; // padding 8*2 + 内容高度
Box itemBox = new Box(x + 8, y, SIDEBAR_WIDTH - 16, itemHeight);

if (active) {
    drawRounded(g, itemBox.x, itemBox.y, itemBox.x + itemBox.width, 
        itemBox.y + itemBox.height, 6, 
        new Color4b(236, 65, 65, 255), Color4b.TRANSPARENT);
}
```

## 5. 播放按钮 (play-btn)

### CSS:
```css
.play-btn { 
  width: 32px; 
  height: 32px; 
  background: #ec4141; 
  border: none; 
  border-radius: 50%; 
  color: #fff; 
  cursor: pointer; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
}
```

### Java翻译:
```java
float btnRadius = 16;
float btnX = centerX;
float btnY = controlY + 10;
drawRounded(g, btnX - btnRadius, btnY - btnRadius, 
    btnX + btnRadius, btnY + btnRadius,
    btnRadius, new Color4b(236, 65, 65, 255), Color4b.TRANSPARENT);
```

## 6. 歌曲行悬停效果 (song-row:hover)

### CSS:
```css
.song-row { cursor: pointer; }
.song-row:hover { background-color: #f5f5f5; }
.song-row.playing { background-color: #fef2f2; }
.song-row.playing .song-title { color: #ec4141; font-weight: bold; }
```

### Java翻译:
```java
Box rowBox = new Box(x, y, width, rowHeight);

if (isPlaying) {
    drawRounded(g, rowBox.x, rowBox.y, rowBox.x + rowBox.width, 
        rowBox.y + rowBox.height, 4,
        new Color4b(254, 242, 242, 255), Color4b.TRANSPARENT);
} else if (hovered(rowBox)) {
    drawRounded(g, rowBox.x, rowBox.y, rowBox.x + rowBox.width, 
        rowBox.y + rowBox.height, 4,
        new Color4b(245, 245, 245, 255), Color4b.TRANSPARENT);
}
```

## 7. 进度条 (progress-bar)

### CSS:
```css
.progress-bar-bg { 
  flex: 1; 
  height: 4px; 
  background: #e5e5e5; 
  border-radius: 2px; 
  cursor: pointer; 
  position: relative; 
}
.progress-bar-fill { 
  width: 0%; 
  height: 100%; 
  background: #ec4141; 
  border-radius: 2px; 
}
```

### Java翻译:
```java
// 背景
drawRounded(g, progressX, progressY, progressX + progressWidth, progressY + 4,
    2, new Color4b(229, 229, 229, 255), Color4b.TRANSPARENT);

// 填充
float fillWidth = progressWidth * progress;
if (fillWidth > 0) {
    drawRounded(g, progressX, progressY, progressX + fillWidth, progressY + 4,
        2, new Color4b(236, 65, 65, 255), Color4b.TRANSPARENT);
}
```

## 关键要点：

1. **颜色精确对应** - #ec4141 = new Color4b(236, 65, 65, 255)
2. **圆角对应** - border-radius: 15px → radius = 15
3. **尺寸对应** - width/height直接转为float
4. **padding对应** - padding: 8px 12px → 需要在计算位置时加上
5. **hover效果** - :hover → if (hovered(box))
6. **active状态** - .active → if (isActive)

## 下一步：
需要实现的关键效果：
1. 阴影效果 (box-shadow) - 需要多层半透明矩形叠加
2. 渐变效果 (gradient) - 需要用多个颜色过渡
3. 图标 - 用texture或更精细的几何绘制
