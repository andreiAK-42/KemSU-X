package com.example.x

import com.example.x.data.remote.KemsuParser
import org.junit.Assert.*
import org.junit.Test

class KemsuParserTest {

    private val html = """
        <html><body><center><table class="tbl" width="99%">
        <tr><th colspan="8" class="bth">Назначенные задания</th></tr>
        <tr><th>Название</th><th>Требуется ли отправка решения</th><th>Комментарий</th>
        <th>Контрольная<br>дата</th><th>Максимальный<br>балл</th><th>Результат</th><th>Состояние</th><th>&nbsp;</th></tr>
        <tr style="background-color: #dcdcdc">
          <td class="td"><b>Теоретические основы цифровой экономики</b></td>
          <td class="td" style="text-align: center"><b>да</b></td>
          <td class="td"><b data-id="comments"></b></td>
          <td class="td" style="text-align: center"><b>31-12-2026 23:59:59</b></td>
          <td class="td" align="center"><b>5</b></td>
          <td class="td" style="text-align: center"><b></b></td>
          <form action="stud/rep/index.htm" method="POST"><input type="hidden" name="id" value="33737">
          <td class="td">&nbsp;</td><td class="td">&nbsp;</td></form>
        </tr>
        <tr onMouseOver="this.style.background = '#E6E6FA'">
          <td>А1 Цифровая экономика и доказательство ценности</td>
          <td style="text-align: center">да</td>
          <td data-id="comments">https://vk.cc/d1mqzk</td>
          <td style="text-align: center">06-10-2026 23:59:59&nbsp;</td>
          <td align="center">1</td>
          <td align="center"></td>
          <td align="center"><i data-id="statusBox" data-flag="">Не просмотрено</i></td>
          <td class="td" align="center"></td>
        </tr>
        <tr onMouseOver="this.style.background = '#E6E6FA'">
          <td>А2 Без дедлайна</td>
          <td style="text-align: center">да</td>
          <td data-id="comments"></td>
          <td style="text-align: center">&nbsp;</td>
          <td align="center">2</td>
          <td align="center"></td>
          <td align="center"><i data-id="statusBox" data-flag="">Не просмотрено</i></td>
          <td class="td" align="center"></td>
        </tr>
        </table></center></body></html>
    """.trimIndent()

    @Test
    fun groupRowIsNotATask_sectionsAssigned_noDeadlineKept() {
        val tasks = KemsuParser.parseTasks(html)
        // Раздел-заголовок не должен стать лабой
        assertEquals(2, tasks.size)
        assertTrue(tasks.all { it.section == "Теоретические основы цифровой экономики" })
        assertEquals("А1 Цифровая экономика и доказательство ценности", tasks[0].title)
        assertEquals("06-10-2026 23:59:59", tasks[0].controlDate)
        assertEquals(1, tasks[0].maxBall)
        assertEquals("", tasks[0].flag)
        // Задание без дедлайна парсится, но дата пустая
        assertEquals("А2 Без дедлайна", tasks[1].title)
        assertTrue(tasks[1].controlDate.isBlank())
    }
}
