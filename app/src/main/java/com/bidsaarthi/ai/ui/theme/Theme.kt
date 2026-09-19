package com.bidsaarthi.ai.ui.theme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
private val C=lightColorScheme(primary=Color(0xFF2859C5),onPrimary=Color.White,primaryContainer=Color(0xFFDCE5FF),secondary=Color(0xFF006C4C),secondaryContainer=Color(0xFF8CF8C8),background=Color(0xFFF8F9FF),surface=Color(0xFFF8F9FF),surfaceVariant=Color(0xFFE2E7F3),error=Color(0xFFBA1A1A))
private val S=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp))
@Composable fun BidSaarthiTheme(content:@Composable()->Unit)=MaterialTheme(colorScheme=C,shapes=S,typography=Typography(),content=content)
