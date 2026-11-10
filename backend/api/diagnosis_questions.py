
QUESTION_SET_VERSION = "v2.1"

STYLE_SCALE = [
    "左にかなり近い",
    "左にやや近い",
    "どちらともいえない",
    "右にやや近い",
    "右にかなり近い",
]

READINESS_SCALE = [
    "全く当てはまらない",
    "あまり当てはまらない",
    "どちらともいえない",
    "やや当てはまる",
    "とても当てはまる",
]

# Q01〜Q12：左右の考え方への5段階回答
STYLE_QUESTIONS = [
    {
        "id": "Q01",
        "axis": "GE",
        "text": "将来の暮らしを思い浮かべるとき",
        "left": "先に理想の暮らしを描く",
        "right": "経験を重ねながら自分に合う暮らしを探す",
    },
    {
        "id": "Q02",
        "axis": "IX",
        "text": "将来の働き方を考えるとき",
        "left": "条件や制度を調べて比較する",
        "right": "体験した感覚や手応えを確かめる",
    },
    {
        "id": "Q03",
        "axis": "PF",
        "text": "数年後に向けて動き出すとき",
        "left": "目標までの段階や期限を先に決める",
        "right": "状況を見ながら進み方を調整する",
    },
    {
        "id": "Q04",
        "axis": "SD",
        "text": "将来について迷いがあるとき",
        "left": "一人で考えを書き出すと整理しやすい",
        "right": "誰かと話すと整理しやすい",
    },
    {
        "id": "Q05",
        "axis": "GE",
        "text": "10年後の自分について考えるとき",
        "left": "まず色々な可能性を広げたい",
        "right": "まず目指す姿を決めたい",
    },
    {
        "id": "Q06",
        "axis": "IX",
        "text": "知らない暮らし方に関心を持ったとき",
        "left": "実践している人の体験に触れたい",
        "right": "費用や条件などの情報を調べたい",
    },
    {
        "id": "Q07",
        "axis": "PF",
        "text": "予定どおり進まなくなったとき",
        "left": "別の方法を試しながら進みたい",
        "right": "計画を組み直して順序を決めたい",
    },
    {
        "id": "Q08",
        "axis": "SD",
        "text": "大切にしたい価値観を整理するとき",
        "left": "人と考えを交わすと気づきが増える",
        "right": "一人で振り返ると気づきが増える",
    },
    {
        "id": "Q09",
        "axis": "GE",
        "text": "これから挑戦することを選ぶとき",
        "left": "実現したい将来像から逆算する",
        "right": "試してみたい経験から選んでいく",
    },
    {
        "id": "Q10",
        "axis": "IX",
        "text": "新しい選択肢を検討するとき",
        "left": "選択肢の長所・短所を整理して判断する",
        "right": "実際に試した際の納得感から判断する",
    },
    {
        "id": "Q11",
        "axis": "PF",
        "text": "将来の方針を決めたあと",
        "left": "必要な行動を具体的な予定に落とし込む",
        "right": "変更できる余地を残して進める",
    },
    {
        "id": "Q12",
        "axis": "SD",
        "text": "新しい意見に触れたあと",
        "left": "自分の中で意味をじっくり整理する",
        "right": "誰かと話して理解を深める",
    },
]

# Q13〜Q20：人生設計の具体化状況
READINESS_QUESTIONS = [
    {
        "id": "Q13",
        "indicator": "V",
        "text": "今の自分が望む暮らしについて、具体的なイメージがある。",
    },
    {
        "id": "Q14",
        "indicator": "V",
        "text": "仕事以外の生活や、自分の時間をどう過ごしたいかを考えている。",
    },
    {
        "id": "Q15",
        "indicator": "V",
        "text": "将来の選択で大切にしたい条件を、自分の言葉で説明できる。",
    },
    {
        "id": "Q16",
        "indicator": "O",
        "text": "自分の周囲でよく見るもの以外にも、多様な生き方があると知っている。",
    },
    {
        "id": "Q17",
        "indicator": "O",
        "text": "仕事・住まい・家族などについて、複数の選択肢を比較したことがある。",
    },
    {
        "id": "Q18",
        "indicator": "O",
        "text": "将来の選択を考えるとき、参考になる情報や体験談を探す方法が分かる。",
    },
    {
        "id": "Q19",
        "indicator": "R",
        "text": "希望する暮らしに必要な条件（お金・時間・経験など）を考えている。",
    },
    {
        "id": "Q20",
        "indicator": "R",
        "text": "将来に向けて、今できることを試したり準備したりしている。",
    },
]

# 16タイプ：コード → 名称・短い説明

TYPE_MAP = {
    "GIPS": {
        "name": "じっくり未来設計",
        "description": "目標を描き、調べて計画し、一人で整理する。",
    },
    "GIPD": {
        "name": "相談型プランナー",
        "description": "目標と情報をもとに、対話して計画を磨く。",
    },
    "GIFS": {
        "name": "堅実な柔軟派",
        "description": "目標を持ち、調べながら柔軟に進路を更新する。",
    },
    "GIFD": {
        "name": "対話型の未来設計",
        "description": "目標と情報を参考に、人と話して方向を調整する。",
    },
    "GXPS": {
        "name": "実感重視の計画派",
        "description": "目標と体験の手応えを結びつけて計画する。",
    },
    "GXPD": {
        "name": "体験共有プランナー",
        "description": "体験を大切にし、人と相談しながら計画にする。",
    },
    "GXFS": {
        "name": "感覚重視の柔軟派",
        "description": "目標を持ちつつ、自分の感覚で進路を変える。",
    },
    "GXFD": {
        "name": "柔軟な対話型",
        "description": "実感と対話を通して柔軟に道を探す。",
    },
    "EIPS": {
        "name": "選択肢重視の計画派",
        "description": "さまざまな未来を情報で比較し、順序立てて試す。",
    },
    "EIPD": {
        "name": "対話で練る将来設計",
        "description": "選択肢を調べ、話し合いながら挑戦を計画する。",
    },
    "EIFS": {
        "name": "情報重視の探索派",
        "description": "情報を集めながら、自分のペースで可能性を探る。",
    },
    "EIFD": {
        "name": "対話で広げる選択肢",
        "description": "調査と対話で選択肢を広げ、柔軟に進む。",
    },
    "EXPS": {
        "name": "体験重視の計画派",
        "description": "経験から可能性を探し、試す計画を立てる。",
    },
    "EXPD": {
        "name": "相談しながら挑戦",
        "description": "体験を共有しながら次の挑戦を計画する。",
    },
    "EXFS": {
        "name": "マイペースな体験重視",
        "description": "経験から学び、自分の判断で柔軟に進む。",
    },
    "EXFD": {
        "name": "対話で探る未来",
        "description": "新しい経験と対話から未来の可能性を広げる。",
    }
}



def get_question_set():
    """Androidへ返す設問セットを生成する"""
    questions = []

    for question in STYLE_QUESTIONS:
        questions.append({
            **question,
            "kind": "style",
            "scale": STYLE_SCALE,
        })

    for question in READINESS_QUESTIONS:
        questions.append({
            **question,
            "kind": "readiness",
            "scale": READINESS_SCALE,
        })

    return {
        "questionSetVersion": QUESTION_SET_VERSION,
        "questions": questions,
    }
