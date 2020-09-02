package `in`.samvidinfotech.beacondemo

import `in`.samvidinfotech.beacondemo.events.AlphabetReadyEvent
import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.util.SparseArray
import org.greenrobot.eventbus.EventBus
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * An IntentService used to load alphabet from file on a worker thread.
 */
class AlphabetLoaderService : IntentService("AlphabetLoaderService") {

    companion object {
        private const val ALPHABET_FILENAME = "in.samvidinfotech.beacondemo.ALPHABET_FILENAME"
        fun createIntent(context: Context, filename: String): Intent {
            val intent = Intent(context, AlphabetLoaderService::class.java)
            intent.putExtra(ALPHABET_FILENAME, filename)
            return intent
        }
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null || !intent.hasExtra(ALPHABET_FILENAME)) return

        val reader = BufferedReader(InputStreamReader(assets.open(intent.getStringExtra(ALPHABET_FILENAME))))
        val alphabet = SparseArray<Char>()
        var line = reader.readLine()
        var index = 0
        do {
            alphabet.append(index++, line.toCharArray()[0])
            line = reader.readLine()
        } while (line != null)
        alphabet.append(index, ' ')
        ConversionUtils.setAlphabet(alphabet)
        EventBus.getDefault().post(AlphabetReadyEvent())
    }

}
