const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "teacher_alice";
const BOB_UID = "teacher_bob";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("config").doc("school").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("questions").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("students").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).collection("results").get());
});

test("Owner can create and update SchoolConfig, cross-user rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("config").doc("school").set({
      userId: ALICE_UID,
      examTitle: "UJIAN ASAS PJOK",
      schoolName: "SDN Nusantara 01",
      teacherName: "Pak Abdul Azis, S.Pd.",
      teacherPassword: "Abdulazis19",
      themeStyle: "TEAL",
      shuffleQuestions: true,
      examDurationMinutes: 90,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("config").doc("school").update({
      examTitle: "UJIAN ASAS PJOK SEMESTER 1",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("config").doc("school").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("config").doc("school").update({
      examTitle: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("config").doc("school").update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can manage ExamQuestions (1-6) with valid schema and query", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("questions").doc("q_1").set({
      userId: ALICE_UID,
      grade: 4,
      orderNumber: 1,
      questionText: "Gerak dasar lokomotor contohnya adalah...",
      optionA: "Berlari",
      optionB: "Membungkuk",
      optionC: "Memutar lengan",
      optionD: "Menekuk lutut",
      correctAnswer: "A",
      explanation: "Berlari berpindah tempat (lokomotor).",
      sourceType: "AI",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("questions").get()
  );

  // Reject invalid grade (grade 7)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("questions").doc("q_bad").set({
      userId: ALICE_UID,
      grade: 7,
      orderNumber: 2,
      questionText: "Invalid grade",
      optionA: "A",
      optionB: "B",
      optionC: "C",
      optionD: "D",
      correctAnswer: "A",
      sourceType: "MANUAL",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject @DocumentId 'id' in payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("questions").doc("q_id").set({
      id: "q_id",
      userId: ALICE_UID,
      grade: 1,
      orderNumber: 1,
      questionText: "Test",
      optionA: "A",
      optionB: "B",
      optionC: "C",
      optionD: "D",
      correctAnswer: "A",
      sourceType: "MANUAL",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner can manage StudentAccounts and ExamResults including cheat logs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("students").doc("s_1").set({
      userId: ALICE_UID,
      fullName: "Budi Santoso",
      grade: 4,
      password: "123",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("results").doc("r_1").set({
      userId: ALICE_UID,
      studentId: "s_1",
      studentName: "Budi Santoso",
      grade: 4,
      examTitle: "UJIAN ASAS PJOK",
      totalQuestions: 10,
      correctCount: 8,
      wrongCount: 2,
      unansweredCount: 0,
      score: 80,
      durationSecondsUsed: 1250,
      autoLocked: false,
      cheatCount: 1,
      cheatLogs: ["10:15:22 - Keluar dari aplikasi saat ujian berlangsung"],
      answerDetails: [
        {
          questionId: "q_1",
          originalNumber: 1,
          questionText: "Gerak dasar lokomotor...",
          selectedAnswer: "A",
          correctAnswer: "A",
          correct: true,
        },
      ],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("results").get());
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("results").doc("r_future").set({
      userId: ALICE_UID,
      studentId: "s_1",
      studentName: "Budi Santoso",
      grade: 4,
      examTitle: "UJIAN ASAS PJOK",
      totalQuestions: 10,
      correctCount: 8,
      wrongCount: 2,
      unansweredCount: 0,
      score: 80,
      durationSecondsUsed: 1250,
      autoLocked: false,
      cheatCount: 0,
      cheatLogs: [],
      answerDetails: [],
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});
