package pt.isec.amov.safetysec.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.isec.amov.safetysec.R
import pt.isec.amov.safetysec.utils.LanguageUtils
@Composable
fun LanguageSelector(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentLang = LanguageUtils.currentCode
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { LanguageUtils.setLocale("pt") },
            modifier = Modifier.size(50.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_brpt),
                contentDescription = stringResource(R.string.desc_lang_pt),
                modifier = Modifier.size(65.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        IconButton(
            onClick = { LanguageUtils.setLocale("en") },
            modifier = Modifier.size(50.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ukusa),
                contentDescription = stringResource(R.string.desc_lang_en),
                modifier = Modifier.size(65.dp)
            )
        }
    }
}